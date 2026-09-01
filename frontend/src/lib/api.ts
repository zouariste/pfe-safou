import axios from "axios";

const baseURL = import.meta.env.VITE_API_BASE_URL || "/api";
const uiBaseURL = import.meta.env.VITE_BASE_PATH || "/";

/**
 * Turns an Axios error into a string that is safe to render.
 *
 * The backend's GlobalExceptionHandler replies with an ApiErrorResponse object
 * ({ error, message }). Putting that object straight into component state and
 * rendering it throws "Objects are not valid as a React child", and since the
 * app has no error boundary that unmounts the whole tree — a blank page instead
 * of an error message.
 */
export function apiErrorMessage(err: any, fallback = "Unexpected error"): string {
    const data = err?.response?.data;
    if (typeof data === "string" && data.trim()) return data;
    if (data && typeof data === "object") {
        const { message, error } = data as { message?: string; error?: string };
        if (error && message) return `${error}: ${message}`;
        if (message) return message;
        if (error) return error;
    }
    return err?.message || fallback;
}

export const api = axios.create({
    baseURL,
    timeout: 120000,
    headers: { "Content-Type": "application/json" },
});

// Attach JWT token to every request if available
api.interceptors.request.use((config) => {
    const token = localStorage.getItem("jwt");
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

/** Pages where we should never force-redirect to /login */
const AUTH_PAGES = ["/login", "/register", "/verify-email", "/reset-password", "/forgot-password", "/student-onboarding"];

function isOnAuthPage(): boolean {
    return AUTH_PAGES.some((p) => window.location.pathname.includes(p));
}

function forceLogout(): void {
    localStorage.removeItem("jwt");
    localStorage.removeItem("authUser");
    window.location.replace(`${uiBaseURL}login`);
}

/** Flag to prevent multiple concurrent /auth/me validation calls */
let isValidatingSession = false;

// Auto-logout on 401; on 403 verify session is still valid before logging out
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const status = error.response?.status;

        if (isOnAuthPage()) {
            return Promise.reject(error);
        }

        // 401 → token invalid/expired → immediate logout
        if (status === 401) {
            forceLogout();
            return Promise.reject(error);
        }

        // 403 → could be role-based (legitimate) or stale session (user deleted / DB reset)
        // Verify by calling /auth/me; if that also fails → session is dead → logout
        if (status === 403 && !isValidatingSession) {
            isValidatingSession = true;
            try {
                await axios.get(`${baseURL}/auth/me`, {
                    headers: { Authorization: `Bearer ${localStorage.getItem("jwt")}` },
                    timeout: 5000,
                });
                // /auth/me succeeded → user exists, this is a genuine 403 (wrong role)
            } catch {
                // /auth/me also failed → session is invalid → logout
                forceLogout();
            } finally {
                isValidatingSession = false;
            }
        }

        return Promise.reject(error);
    }
);
