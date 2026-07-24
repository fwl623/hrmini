package com.company.hrms.module.auth.crypto;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import java.util.UUID;

/**
 * 登录密码传输加密：服务端持有 RSA-2048 密钥对，前端用公钥 RSA-OAEP(SHA-256) 加密后提交。
 * <p>
 * 说明：这消除的是 Network 面板 / 中间人抓到的明文；HTTPS 仍是传输层正途。
 * 库内存储仍为 BCrypt，与本类无关。
 */
@Service
public class LoginRsaCryptoService {

    private static final Logger log = LoggerFactory.getLogger(LoginRsaCryptoService.class);

    public static final String ALGORITHM = "RSA-OAEP";
    public static final String HASH = "SHA-256";

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private String publicKeySpkiBase64;
    private String keyId;

    @PostConstruct
    public void init() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048, new SecureRandom());
        KeyPair pair = gen.generateKeyPair();
        this.privateKey = pair.getPrivate();
        this.publicKey = pair.getPublic();
        this.publicKeySpkiBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        this.keyId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("Login RSA key pair ready, keyId={}", keyId);
    }

    public String getAlgorithm() {
        return ALGORITHM;
    }

    public String getHash() {
        return HASH;
    }

    public String getKeyId() {
        return keyId;
    }

    /** X.509 SubjectPublicKeyInfo，Base64（无 PEM 头尾），供 Web Crypto importKey('spki') */
    public String getPublicKeySpkiBase64() {
        return publicKeySpkiBase64;
    }

    /**
     * 解密前端密文。失败统一按参数错误处理，避免泄露细节。
     */
    public String decryptPassword(String cipherBase64) {
        if (cipherBase64 == null || cipherBase64.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "登录凭据无效");
        }
        try {
            byte[] cipherBytes = Base64.getDecoder().decode(cipherBase64.trim());
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            OAEPParameterSpec oaep = new OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, oaep);
            byte[] plain = cipher.doFinal(cipherBytes);
            String password = new String(plain, StandardCharsets.UTF_8);
            if (password.isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "登录凭据无效");
            }
            return password;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Login password decrypt failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.PARAM_INVALID, "登录凭据解密失败，请刷新页面重试");
        }
    }
}
