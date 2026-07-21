import { request } from '@umijs/max';
import { API_BASE } from '@/constants/roles';

export type UploadFileResult = {
  url: string;
  fileName: string;
  storedName: string;
};

/** 上传本地文件（图片/文档），返回可访问 URL */
export async function uploadFile(file: File) {
  const form = new FormData();
  form.append('file', file);
  return request<API.Result<UploadFileResult>>(`${API_BASE}/files`, {
    method: 'POST',
    data: form,
  });
}
