import type {
  CreateSiteRequest,
  EmbedCode,
  PaginatedResponse,
  ReplaceAllowedOriginsRequest,
  Site,
  UpdateSiteRequest,
} from '../types/api'
import { apiClient } from './client'

export interface ListSitesParams {
  page?: number
  pageSize?: number
}

export async function listSites(params: ListSitesParams = {}): Promise<PaginatedResponse<Site>> {
  const response = await apiClient.get<PaginatedResponse<Site>>('/sites', { params })
  assertPaginatedSiteResponse(response.data)
  return response.data
}

export async function createSite(request: CreateSiteRequest): Promise<Site> {
  const response = await apiClient.post<Site>('/sites', request)
  return response.data
}

export async function getSite(siteId: string): Promise<Site> {
  const response = await apiClient.get<Site>(`/sites/${siteId}`)
  return response.data
}

export async function updateSite(siteId: string, request: UpdateSiteRequest): Promise<Site> {
  const response = await apiClient.patch<Site>(`/sites/${siteId}`, request)
  return response.data
}

export async function replaceAllowedOrigins(
  siteId: string,
  request: ReplaceAllowedOriginsRequest,
): Promise<Site> {
  const response = await apiClient.put<Site>(`/sites/${siteId}/allowed-origins`, request)
  return response.data
}

export async function getEmbedCode(siteId: string): Promise<EmbedCode> {
  const response = await apiClient.get<EmbedCode>(`/sites/${siteId}/embed-code`)
  return response.data
}

export async function deleteSite(siteId: string): Promise<void> {
  await apiClient.delete(`/sites/${siteId}`)
}

export async function listAllSites(): Promise<Site[]> {
  const pageSize = 100
  const firstPage = await listSites({ page: 1, pageSize })
  const allSites = [...firstPage.items]

  for (let page = 2; page <= firstPage.totalPages; page += 1) {
    const response = await listSites({ page, pageSize })
    allSites.push(...response.items)
  }

  return allSites
}

function assertPaginatedSiteResponse(value: PaginatedResponse<Site>): void {
  if (!value || !Array.isArray(value.items)) {
    throw new Error('Некорректный ответ API со списком сайтов.')
  }
}
