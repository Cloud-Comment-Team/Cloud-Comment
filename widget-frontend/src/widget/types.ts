import type { WidgetApiClient } from "./api";

export type CloudCommentWidgetOptions = {
  siteId: string;
  apiBaseUrl?: string;
  frameBaseUrl?: string;
  pageUrl?: string;
  target?: string | HTMLElement;
  theme?: WidgetTheme;
};

export type WidgetTheme = "auto" | "light" | "dark";

export type WidgetStyleTheme = "AUTO" | "LIGHT" | "DARK";

export type WidgetCornerRadius = "SMALL" | "MEDIUM" | "LARGE";

export type WidgetDensity = "COMFORTABLE" | "COMPACT";
export type WidgetContentWidth = "READABLE" | "WIDE" | "FULL";
export type WidgetAlignment = "LEFT" | "CENTER";
export type WidgetFontScale = "SMALL" | "MEDIUM" | "LARGE";
export type WidgetFontFamily = "INHERIT" | "SYSTEM" | "SERIF" | "MONO";
export type WidgetComposerPosition = "TOP" | "BOTTOM";
export type WidgetAvatarStyle = "INITIALS" | "HIDDEN";
export type WidgetElevation = "BORDER" | "SHADOW" | "NONE";
export type WidgetLocale = "RU" | "EN";

export type WidgetStyle = {
  version: number;
  theme: WidgetStyleTheme;
  accentColor: string;
  cornerRadius: WidgetCornerRadius;
  density: WidgetDensity;
  contentWidth: WidgetContentWidth;
  alignment: WidgetAlignment;
  fontScale: WidgetFontScale;
  fontFamily: WidgetFontFamily;
  showHeader: boolean;
  headerTitle: string;
  composerPosition: WidgetComposerPosition;
  defaultSort: PublicCommentSort;
  showSort: boolean;
  enabledReactions: CommentReactionType[];
  avatarStyle: WidgetAvatarStyle;
  elevation: WidgetElevation;
  locale: WidgetLocale;
  commentsTitle: string;
  composerPlaceholder: string;
  emptyMessage: string;
};

export type CloudCommentWidgetInstance = {
  destroy: () => void;
};

export type CloudCommentWidgetApi = {
  init: (options: CloudCommentWidgetOptions) => CloudCommentWidgetInstance;
  autoInit: () => CloudCommentWidgetInstance | null;
};

export type ModerationMode = "PRE_MODERATION" | "POST_MODERATION";

export type CommentStatus = "PENDING" | "APPROVED" | "REJECTED" | "HIDDEN" | "SPAM";

export type CommentReactionType = "LIKE" | "LOVE" | "LAUGH" | "WOW";

export type PublicCommentSort = "PINNED_FIRST" | "NEWEST" | "OLDEST" | "TOP_REACTIONS";

export type CommentReaction = {
  type: CommentReactionType;
  emoji: string;
  label: string;
  count: number;
  reactedByCurrentUser: boolean;
};

export type CommentReactionsResponse = {
  reactions: CommentReaction[];
};

export type CommentAuthor = {
  id: string;
  displayName: string | null;
  kind?: "VISITOR" | "OWNER";
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
  editedAt: string | null;
  pinned: boolean;
  ownedByCurrentUser: boolean;
  reactions: CommentReaction[];
  replyCount: number;
  replies: PublicComment[];
};

export type PublicWidgetConfig = {
  siteId: string;
  moderationMode: ModerationMode;
  style: WidgetStyle;
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
    CloudCommentWidgetFrame?: {
      previewInit: (api: WidgetApiClient) => CloudCommentWidgetInstance;
    };
  }
}
