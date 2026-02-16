import Axios, { type AxiosRequestConfig } from "axios";

export const AXIOS_INSTANCE = Axios.create({
  baseURL: "",
  timeout: 30_000, // 30 saniye
});

export const customInstance = <T>(config: AxiosRequestConfig): Promise<T> => {
  const controller = new AbortController();
  const promise = AXIOS_INSTANCE({
    ...config,
    signal: controller.signal,
  }).then(({ data }) => data);

  // @ts-expect-error cancel property is used by react-query
  promise.cancel = () => {
    controller.abort();
  };

  return promise;
};

export default customInstance;
