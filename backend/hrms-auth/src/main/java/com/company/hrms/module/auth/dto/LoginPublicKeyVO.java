package com.company.hrms.module.auth.dto;

/**
 * 登录页拉取的 RSA 公钥（Web Crypto SPKI Base64）。
 */
public class LoginPublicKeyVO {

    private String keyId;
    private String algorithm;
    private String hash;
    /** Base64(SubjectPublicKeyInfo)，无 PEM 头尾 */
    private String publicKey;

    public LoginPublicKeyVO() {
    }

    public LoginPublicKeyVO(String keyId, String algorithm, String hash, String publicKey) {
        this.keyId = keyId;
        this.algorithm = algorithm;
        this.hash = hash;
        this.publicKey = publicKey;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }
}
