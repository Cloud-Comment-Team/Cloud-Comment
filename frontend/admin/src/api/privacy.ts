import { apiClient } from './client'

export interface ConsentRequirements {
  privacyPolicyVersion: string
  termsVersion: string
  privacyPolicyUrl: string
  termsUrl: string
  personalDataNoticeUrl: string
  dataExportInfoUrl: string
}

export async function getConsentRequirements(): Promise<ConsentRequirements> {
  const response = await apiClient.get<ConsentRequirements>('/privacy/consent-requirements')
  return response.data
}
