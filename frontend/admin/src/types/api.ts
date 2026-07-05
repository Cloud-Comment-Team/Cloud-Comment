export type ModerationMode = 'PRE_MODERATION' | 'POST_MODERATION' | 'DISABLED'

export type WidgetTheme = 'AUTO' | 'LIGHT' | 'DARK'

export type WidgetCornerRadius = 'SMALL' | 'MEDIUM' | 'LARGE'

export type AutoModerationStrictness = 'OFF' | 'RELAXED' | 'BALANCED' | 'STRICT'

export interface WidgetStyle {
  theme: WidgetTheme
  accentColor: string
  cornerRadius: WidgetCornerRadius
}

export interface AutoModerationSettings {
  enabled: boolean
  strictness: AutoModerationStrictness
  blockedWords: string[]
  holdLinks: boolean
  blockLinks: boolean
  maxLinks: number
}

export interface AutoModerationSignal {
  category: string
  score: number
  reason: string
}

export interface AutoModerationCheckResponse {
  status: CommentStatus
  score: number
  reason: string | null
  signals: AutoModerationSignal[]
}

export type CommentStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'HIDDEN' | 'SPAM'

export type ModerationPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export type ModerationActionType = 'APPROVE' | 'REJECT' | 'HIDE' | 'MARK_SPAM' | 'RESTORE'

export interface PaginatedResponse<T> {
  items: T[]
  page: number
  pageSize: number
  totalItems: number
  totalPages: number
}

export interface Site {
  id: string
  ownerId: string
  name: string
  domain: string
  publicKey: string
  moderationMode: ModerationMode
  isActive: boolean
  widgetStyle: WidgetStyle
  autoModeration: AutoModerationSettings
  allowedOrigins: string[]
  createdAt: string
  updatedAt: string
}

export interface CreateSiteRequest {
  name: string
  domain: string
  moderationMode: ModerationMode
  allowedOrigins: string[]
  widgetStyle?: WidgetStyle
  autoModeration?: AutoModerationSettings
}

export interface UpdateSiteRequest {
  name?: string
  domain?: string
  moderationMode?: ModerationMode
  isActive?: boolean
  widgetStyle?: WidgetStyle
  autoModeration?: AutoModerationSettings
}

export interface ReplaceAllowedOriginsRequest {
  allowedOrigins: string[]
}

export interface EmbedCode {
  siteId: string
  scriptUrl: string
  embedCode: string
  dataAttributes: Record<string, string>
}

export interface CommentAuthor {
  id: string | null
  email: string | null
  displayName: string | null
}

export interface ParentComment {
  id: string
  author: CommentAuthor | null
  content: string
  status: CommentStatus
  createdAt: string
}

export interface Comment {
  id: string
  siteId: string
  pageId: string
  pageUrl: string
  parentId: string | null
  parent: ParentComment | null
  author: CommentAuthor | null
  content: string
  status: CommentStatus
  moderationReason: string | null
  priority: ModerationPriority
  priorityScore: number
  priorityReasons: string[]
  createdAt: string
  updatedAt: string
  replies: Comment[]
}

export interface ModerationActionRequest {
  action: ModerationActionType
  reason?: string | null
}

export interface ModerationAction {
  id: string
  commentId: string
  action: ModerationActionType
  fromStatus: CommentStatus
  toStatus: CommentStatus
  reason: string | null
  performedBy: {
    id: string
    email: string
  }
  createdAt: string
}

export interface ListCommentsParams {
  siteId?: string
  pageId?: string
  pageUrl?: string
  status?: CommentStatus
  createdFrom?: string
  createdTo?: string
  search?: string
  sortBy?: 'SMART' | 'CREATED_AT' | 'UPDATED_AT' | 'STATUS'
  sortOrder?: 'ASC' | 'DESC'
  page?: number
  pageSize?: number
}

export type AnalyticsRange = '7d' | '30d' | '90d' | 'all'

export interface AnalyticsSummary {
  sites: number
  pages: number
  comments: number
  replies: number
  reactions: number
  pending: number
  approved: number
  rejected: number
  hidden: number
  spam: number
}

export interface CommentTimePoint {
  bucket: string
  total: number
  approved: number
  pending: number
  spam: number
}

export interface ModerationStatusCount {
  status: CommentStatus
  count: number
}

export interface ReactionTypeCount {
  type: 'LIKE' | 'LOVE' | 'LAUGH' | 'WOW'
  count: number
}

export interface TopPageAnalytics {
  pageId: string
  siteId: string
  siteName: string
  pageUrl: string
  comments: number
  replies: number
  reactions: number
  approved: number
  pending: number
  spam: number
  lastCommentAt: string | null
}

export interface ActiveCommenter {
  userId: string | null
  email: string | null
  displayName: string | null
  comments: number
  approved: number
  pending: number
  rejectedOrSpam: number
  lastActivityAt: string | null
}

export interface OwnerAnalytics {
  range: AnalyticsRange
  siteId: string | null
  from: string | null
  to: string
  summary: AnalyticsSummary
  commentsOverTime: CommentTimePoint[]
  moderationFunnel: ModerationStatusCount[]
  reactionDistribution: ReactionTypeCount[]
  topPages: TopPageAnalytics[]
  activeCommenters: ActiveCommenter[]
}
