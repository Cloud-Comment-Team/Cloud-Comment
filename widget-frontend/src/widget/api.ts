import type {
  AccountDeletionRequest,
  AuthUser,
  CommentReactionType,
  CommentReactionsResponse,
  CommentPermalinkLocation,
  LoginResponse,
  PaginatedResponse,
  PublicComment,
  PublicCommentSort,
  PublicWidgetConfig
} from "./types";
import { WIDGET_CONTEXT_HEADER, WIDGET_PAGE_URL_HEADER } from "./protocol";

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

export type ConsentRequirements = {
  privacyPolicyVersion: string;
  termsVersion: string;
  privacyPolicyUrl: string;
  termsUrl: string;
  personalDataNoticeUrl: string;
  dataExportInfoUrl: string;
};

export type RegisterPayload = {
  email: string;
  password: string;
  acceptedPrivacyPolicy: boolean;
  acceptedTerms: boolean;
  privacyPolicyVersion: string;
  termsVersion: string;
};

export type WidgetApiClient = {
  getConfig: () => Promise<PublicWidgetConfig>;
  getConsentRequirements: () => Promise<ConsentRequirements>;
  listComments: (
    sort: PublicCommentSort,
    page: number,
    pageSize: number,
    token?: string | null
  ) => Promise<PaginatedResponse<PublicComment>>;
  listReplies: (commentId: string, page: number, pageSize: number, token?: string | null) => Promise<PaginatedResponse<PublicComment>>;
  locateComment: (commentId: string, sort: PublicCommentSort, pageSize: number, token?: string | null) => Promise<CommentPermalinkLocation>;
  createComment: (content: string, parentId: string | null, token: string) => Promise<PublicComment>;
  updateComment: (commentId: string, content: string, token: string) => Promise<PublicComment>;
  deleteComment: (commentId: string, token: string) => Promise<void>;
  setReaction: (
    commentId: string,
    type: CommentReactionType | null,
    token: string
  ) => Promise<CommentReactionsResponse>;
  getCurrentUser: (token: string) => Promise<AuthUser>;
  login: (email: string, password: string) => Promise<LoginResponse>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: (token: string) => Promise<void>;
  requestAccountDeletion: (token: string) => Promise<AccountDeletionRequest>;
  exportPersonalData: (token: string) => Promise<unknown>;
};

export function createWidgetApiClient(
  apiBaseUrl: string,
  siteId: string,
  pageUrl: string,
  contextToken: string
): WidgetApiClient {
  if (!contextToken.trim()) {
    throw new Error("CloudComment frame context is required");
  }
  const baseUrl = apiBaseUrl.replace(/\/+$/, "");
  const siteBasePath = `/public/sites/${encodeURIComponent(siteId)}`;

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const siteRequest = path.startsWith(siteBasePath);
    const pageRequest = path.startsWith(`${siteBasePath}/pages/comments`)
      || path.startsWith(`${siteBasePath}/comments/`);
    let response: Response;
    try {
      response = await fetch(`${baseUrl}${path}`, {
        ...init,
        mode: "cors",
        credentials: "omit",
        cache: "no-store",
        headers: {
          Accept: "application/json",
          ...(siteRequest ? { [WIDGET_CONTEXT_HEADER]: contextToken } : {}),
          ...(pageRequest ? { [WIDGET_PAGE_URL_HEADER]: pageUrl } : {}),
          ...init?.headers
        }
      });
    } catch {
      throw new WidgetApiError("CloudComment временно недоступен. Попробуйте позже.");
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
    getConsentRequirements: () => request<ConsentRequirements>(`${siteBasePath}/privacy/consent-requirements`),
    listComments: (sort, page, pageSize, token) => {
      const params = new URLSearchParams({
        pageUrl,
        sort,
        page: String(page),
        pageSize: String(pageSize),
        replyLimit: "3"
      });
      return request<PaginatedResponse<PublicComment>>(`${siteBasePath}/pages/comments?${params}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : undefined
      });
    },
    listReplies: (commentId, page, pageSize, token) => {
      const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
      return request<PaginatedResponse<PublicComment>>(
        `${siteBasePath}/comments/${encodeURIComponent(commentId)}/replies?${params}`,
        { headers: token ? { Authorization: `Bearer ${token}` } : undefined }
      );
    },
    locateComment: (commentId, sort, pageSize, token) => {
      const params = new URLSearchParams({ pageUrl, sort, pageSize: String(pageSize) });
      return request<CommentPermalinkLocation>(
        `${siteBasePath}/comments/${encodeURIComponent(commentId)}/permalink?${params}`,
        { headers: token ? { Authorization: `Bearer ${token}` } : undefined }
      );
    },
    createComment: (content, parentId, token) =>
      request<PublicComment>(`${siteBasePath}/pages/comments`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          pageUrl,
          parentId,
          content
        })
      }),
    updateComment: (commentId, content, token) =>
      request<PublicComment>(`${siteBasePath}/comments/${encodeURIComponent(commentId)}`, {
        method: "PATCH",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ content })
      }),
    deleteComment: (commentId, token) =>
      request<void>(`${siteBasePath}/comments/${encodeURIComponent(commentId)}`, {
        method: "DELETE",
        headers: {
          Authorization: `Bearer ${token}`
        }
      }),
    setReaction: (commentId, type, token) =>
      request<CommentReactionsResponse>(`${siteBasePath}/comments/${encodeURIComponent(commentId)}/reaction`, {
        method: "PUT",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ type })
      }),
    getCurrentUser: (token) =>
      request<AuthUser>(`${siteBasePath}/auth/me`, {
        headers: {
          Authorization: `Bearer ${token}`
        }
      }),
    login: (email, password) =>
      request<LoginResponse>(`${siteBasePath}/auth/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({ email, password })
      }),
    register: (payload) =>
      request<void>(`${siteBasePath}/auth/register`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
      }),
    logout: (token) =>
      request<void>(`${siteBasePath}/auth/logout`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`
        }
      }),
    requestAccountDeletion: (token) =>
      request<AccountDeletionRequest>(`${siteBasePath}/account/deletion-requests`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`
        }
      }),
    exportPersonalData: (token) =>
      request<unknown>(`${siteBasePath}/account/personal-data`, {
        headers: {
          Authorization: `Bearer ${token}`
        }
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
    return "Войдите, чтобы оставить комментарий.";
  }

  if (response.status === 404) {
    return "Комментарии недоступны для этого сайта или страницы.";
  }

  return "CloudComment не смог обработать запрос. Попробуйте еще раз.";
}
