import axios from "axios";

const client = axios.create({ baseURL: "/api" });

client.interceptors.request.use((config) => {
  const access = localStorage.getItem("accessToken");
  if (access) {
    config.headers.Authorization = `Bearer ${access}`;
  }
  return config;
});

let refreshing = false;

client.interceptors.response.use(
  (r) => r,
  async (err) => {
    const original = err.config;
    const url = String(original?.url || "");
    if (url.includes("/auth/login") || url.includes("/auth/refresh")) {
      return Promise.reject(err);
    }
    if (err.response?.status === 401 && !(original as { _retry?: boolean })._retry) {
      (original as { _retry?: boolean })._retry = true;
      const rt = localStorage.getItem("refreshToken");
      if (!rt) {
        localStorage.clear();
        window.location.href = "/login";
        return Promise.reject(err);
      }
      if (refreshing) {
        return Promise.reject(err);
      }
      refreshing = true;
      try {
        const { data } = await axios.post("/api/auth/refresh", { refreshToken: rt });
        localStorage.setItem("accessToken", data.accessToken);
        localStorage.setItem("refreshToken", data.refreshToken);
        original.headers.Authorization = `Bearer ${data.accessToken}`;
        refreshing = false;
        return client(original);
      } catch {
        refreshing = false;
        localStorage.clear();
        window.location.href = "/login";
      }
    }
    return Promise.reject(err);
  }
);

export default client;
