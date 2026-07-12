export type ModerationMode = 'PRE_MODERATION' | 'POST_MODERATION' | 'DISABLED'

export type WidgetTheme = 'AUTO' | 'LIGHT' | 'DARK'

export type WidgetCornerRadius = 'SMALL' | 'MEDIUM' | 'LARGE'
export type WidgetDensity = 'COMFORTABLE' | 'COMPACT'
export type WidgetContentWidth = 'READABLE' | 'WIDE' | 'FULL'
export type WidgetAlignment = 'LEFT' | 'CENTER'
export type WidgetFontScale = 'SMALL' | 'MEDIUM' | 'LARGE'
export type WidgetFontFamily = 'INHERIT' | 'SYSTEM' | 'SERIF' | 'MONO'
export type WidgetComposerPosition = 'TOP' | 'BOTTOM'
export type WidgetAvatarStyle = 'INITIALS' | 'HIDDEN'
export type WidgetElevation = 'BORDER' | 'SHADOW' | 'NONE'
export type WidgetLocale = 'RU' | 'EN'
export type CommentReactionType = 'LIKE' | 'LOVE' | 'LAUGH' | 'WOW'
export type PublicCommentSort = 'PINNED_FIRST' | 'NEWEST' | 'OLDEST' | 'TOP_REACTIONS'

export type AutoModerationStrictness = 'OFF' | 'RELAXED' | 'BALANCED' | 'STRICT'

export interface WidgetStyle {
  version: number
  theme: WidgetTheme
  accentColor: string
  cornerRadius: WidgetCornerRadius
  density: WidgetDensity
  contentWidth: WidgetContentWidth
  alignment: WidgetAlignment
  fontScale: WidgetFontScale
  fontFamily: WidgetFontFamily
  showHeader: boolean
  headerTitle: string
  composerPosition: WidgetComposerPosition
  defaultSort: PublicCommentSort
  showSort: boolean
  enabledReactions: CommentReactionType[]
  avatarStyle: WidgetAvatarStyle
  elevation: WidgetElevation
  locale: WidgetLocale
  commentsTitle: string
  composerPlaceholder: string
  emptyMessage: string
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

export type ModerationActionType = 'APPROVE' | 'REJECT' | 'HIDE' | 'MARK_SPAM' | 'RESTORE' | 'UNDO'
export type ModerationCommand = Exclude<ModerationActionType, 'UNDO'>

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
  pinned: boolean
  favorite: boolean
  priority: ModerationPriority
  priorityScore: number
  priorityReasons: string[]
  createdAt: string
  updatedAt: string
  replies: Comment[]
}

export interface ModerationActionRequest {
  action: ModerationCommand
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
  operationId: string | null
  revertsActionId: string | null
  createdAt: string
}

export interface BulkModerationRequest {
  operationId: string
  commentIds: string[]
  action: ModerationCommand
  reason?: string | null
}

export interface BulkModerationResponse {
  items: Array<{
    commentId: string
    success: boolean
    action: ModerationAction | null
    errorCode: string | null
    message: string | null
  }>
}

export interface ModerationCounts {
  statuses: Record<CommentStatus, number>
  requiringDecision: number
}

export interface ListCommentsParams {
  siteId?: string
  pageId?: string
  pageUrl?: string
  status?: CommentStatus
  statuses?: CommentStatus[]
  createdFrom?: string
  createdTo?: string
  search?: string
  favorite?: boolean
  sortBy?: 'SMART' | 'CREATED_AT' | 'UPDATED_AT' | 'STATUS' | 'PINNED' | 'FAVORITE'
  sortOrder?: 'ASC' | 'DESC'
  page?: number
  pageSize?: number
}

export interface UpdateCommentFlagsRequest {
  pinned?: boolean
  favorite?: boolean
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

export interface RealtimeEnvelope<TPayload> {
  type: string
  sentAt?: string
  payload: TPayload
}

export interface NewCommentNotification {
  notificationId: string
  commentId: string
  siteId: string
  siteName: string
  pageId: string
  pageUrl: string
  parentId: string | null
  authorEmail: string | null
  contentPreview: string
  status: CommentStatus
  createdAt: string
}

export interface OwnerNotification {
  id: string
  commentId: string
  siteId: string
  siteName: string
  pageId: string
  pageUrl: string
  parentId: string | null
  authorEmail: string | null
  contentPreview: string
  status: CommentStatus
  readAt: string | null
  createdAt: string
}

export interface ModerationActionNotification {
  siteId: string
  pageId: string
  commentId: string
  action: ModerationActionType
  fromStatus: CommentStatus
  toStatus: CommentStatus
  reason: string | null
  moderatorId: string
  createdAt: string
}

export type RealtimeEvent =
  | (RealtimeEnvelope<NewCommentNotification> & { type: 'comment.created' })
  | (RealtimeEnvelope<ModerationActionNotification> & { type: 'comment.moderation_action_applied' })
