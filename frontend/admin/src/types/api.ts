export type ModerationMode = 'PRE_MODERATION' | 'POST_MODERATION' | 'DISABLED'

export type WidgetTheme = 'AUTO' | 'LIGHT' | 'DARK'

export type WidgetCornerRadius = 'SMALL' | 'MEDIUM' | 'LARGE'

export interface WidgetStyle {
  theme: WidgetTheme
  accentColor: string
  cornerRadius: WidgetCornerRadius
}

export type CommentStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'HIDDEN' | 'SPAM'

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
}

export interface UpdateSiteRequest {
  name?: string
  domain?: string
  moderationMode?: ModerationMode
  isActive?: boolean
  widgetStyle?: WidgetStyle
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
  sortBy?: 'CREATED_AT' | 'UPDATED_AT' | 'STATUS'
  sortOrder?: 'ASC' | 'DESC'
  page?: number
  pageSize?: number
}
