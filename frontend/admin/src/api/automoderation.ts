import type {
  AutoModerationPoliciesResponse,
  AutoModerationExecutionMode,
  AutoModerationPolicy,
  AutoModerationPolicyPreset,
  AutoModerationPolicyUpdateRequest,
  AutoModerationSimulationResponse,
} from '../types/api'
import { apiClient } from './client'

export async function getAutoModerationPolicies(siteId: string): Promise<AutoModerationPoliciesResponse> {
  const response = await apiClient.get<AutoModerationPoliciesResponse>(
    `/sites/${siteId}/automoderation/policies`,
  )
  return response.data
}

export async function createAutoModerationDraft(
  siteId: string,
  preset: AutoModerationPolicyPreset,
  enabled: boolean,
  executionMode: AutoModerationExecutionMode,
): Promise<AutoModerationPolicy> {
  const response = await apiClient.post<AutoModerationPolicy>(
    `/sites/${siteId}/automoderation/policies`,
    { preset, enabled, executionMode },
  )
  return response.data
}

export async function updateAutoModerationDraft(
  siteId: string,
  policyId: string,
  request: AutoModerationPolicyUpdateRequest,
): Promise<AutoModerationPolicy> {
  const response = await apiClient.patch<AutoModerationPolicy>(
    `/sites/${siteId}/automoderation/policies/${policyId}`,
    request,
  )
  return response.data
}

export async function deleteAutoModerationDraft(
  siteId: string,
  policyId: string,
  expectedRevision: number,
): Promise<void> {
  await apiClient.delete(`/sites/${siteId}/automoderation/policies/${policyId}`, {
    params: { expectedRevision },
  })
}

export async function simulateAutoModerationPolicy(
  siteId: string,
  policyId: string,
  content: string,
): Promise<AutoModerationSimulationResponse> {
  const response = await apiClient.post<AutoModerationSimulationResponse>(
    `/sites/${siteId}/automoderation/policies/${policyId}/simulate`,
    { content },
  )
  return response.data
}

export async function publishAutoModerationDraft(
  siteId: string,
  policyId: string,
  expectedRevision: number,
  expectedActiveVersionId: string | null,
): Promise<AutoModerationPolicy> {
  const response = await apiClient.post<AutoModerationPolicy>(
    `/sites/${siteId}/automoderation/policies/${policyId}/publish`,
    { expectedRevision, expectedActiveVersionId },
  )
  return response.data
}

export async function rollbackAutoModerationPolicy(
  siteId: string,
  versionId: string,
  expectedActiveVersionId: string | null,
): Promise<AutoModerationPolicy> {
  const response = await apiClient.post<AutoModerationPolicy>(
    `/sites/${siteId}/automoderation/versions/${versionId}/rollback`,
    { expectedActiveVersionId },
  )
  return response.data
}
