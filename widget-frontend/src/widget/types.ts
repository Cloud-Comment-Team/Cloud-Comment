export type CloudCommentWidgetOptions = {
  siteId: string;
  apiBaseUrl?: string;
  pageUrl?: string;
  target?: string | HTMLElement;
  theme?: WidgetTheme;
};

export type WidgetTheme = "auto" | "light" | "dark";

export type CloudCommentWidgetInstance = {
  destroy: () => void;
};

export type CloudCommentWidgetApi = {
  init: (options: CloudCommentWidgetOptions) => CloudCommentWidgetInstance;
  autoInit: () => CloudCommentWidgetInstance | null;
};

export type ModerationMode = "PRE_MODERATION" | "POST_MODERATION";

export type CommentStatus = "PENDING" | "APPROVED" | "REJECTED" | "HIDDEN" | "SPAM";

export type CommentAuthor = {
  id: string;
  email: string;
  displayName: string | null;
};

export type PublicComment = {
  id: string;
  siteId: string;
  pageId: string;
  parentId: string | null;
  author: CommentAuthor;
  content: string;
  status: CommentStatus;
  createdAt: string;
  updatedAt: string;
  replies: PublicComment[];
};

export type PublicWidgetConfig = {
  siteId: string;
  moderationMode: ModerationMode;
};

export type ConsentRequirements = {
  privacyPolicyVersion: string;
  termsVersion: string;
  privacyPolicyUrl: string;
  termsUrl: string;
  personalDataNoticeUrl: string;
  dataExportInfoUrl: string;
};

export type PaginatedResponse<T> = {
  items: T[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
};

export type AuthUser = {
  id: string;
  email: string;
  roles: string[];
  createdAt: string;
  updatedAt: string;
};

export type LoginResponse = {
  token: string;
  tokenType: string;
  expiresAt: string;
  user: AuthUser;
};

export type AccountDeletionRequest = {
  id: string;
  userId: string;
  status: string;
  createdAt: string;
  expiresAt: string;
};

declare global {
  interface Window {
    CloudCommentWidget?: CloudCommentWidgetApi;
  }
}
