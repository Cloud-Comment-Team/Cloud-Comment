import type {
  LoginResponse,
  PaginatedResponse,
  PublicComment,
  PublicWidgetConfig
} from "./types";

type ApiErrorBody = {
  error?: {
    message?: string;
  };
};

export class WidgetApiError extends Error {
  constructor(
    message: string,
    public readonly status?: number
  ) {
    super(message);
    this.name = "WidgetApiError";
  }
}

export type WidgetApiClient = {
  getConfig: () => Promise<PublicWidgetConfig>;
  listComments: () => Promise<PaginatedResponse<PublicComment>>;
  createComment: (content: string, token: string) => Promise<PublicComment>;
  login: (email: string, password: string) => Promise<LoginResponse>;
  register: (email: string, password: string) => Promise<void>;
};

export function createWidgetApiClient(
  apiBaseUrl: string,
  siteId: string,
  pageUrl: string
): WidgetApiClient {
  const baseUrl = apiBaseUrl.replace(/\/+$/, "");
  const siteBasePath = `/public/sites/${encodeURIComponent(siteId)}`;

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    let response: Response;
    try {
      response = await fetch(`${baseUrl}${path}`, {
        ...init,
        headers: {
          Accept: "application/json",
          ...init?.headers
        }
      });
    } catch {
      throw new WidgetApiError("CloudComment is temporarily unavailable. Please try again later.");
    }

    if (!response.ok) {
      throw new WidgetApiError(await readErrorMessage(response), response.status);
    }

    if (response.status === 204) {
      return undefined as T;
    }

    return response.json() as Promise<T>;
  }

  return {
    getConfig: () => request<PublicWidgetConfig>(`${siteBasePath}/config`),
    listComments: () => {
      const params = new URLSearchParams({
        pageUrl,
        page: "1",
        pageSize: "20"
      });
      return request<PaginatedResponse<PublicComment>>(`${siteBasePath}/pages/comments?${params}`);
    },
    createComment: (content, token) =>
      request<PublicComment>(`${siteBasePath}/pages/comments`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          pageUrl,
          parentId: null,
          content
        })
      }),
    login: (email, password) =>
      request<LoginResponse>(`${siteBasePath}/auth/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ email, password })
      }),
    register: (email, password) =>
      request<void>(`${siteBasePath}/auth/register`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ email, password })
      })
  };
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as ApiErrorBody;
    if (body.error?.message) {
      return body.error.message;
    }
  } catch {
    // Fall through to status-based messages.
  }

  if (response.status === 401) {
    return "Please sign in before posting a comment.";
  }

  if (response.status === 404) {
    return "Comments are not available for this site or page.";
  }

  return "CloudComment could not process the request. Please try again.";
}
