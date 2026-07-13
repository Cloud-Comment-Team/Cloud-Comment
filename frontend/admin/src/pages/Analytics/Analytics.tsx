import { OwnerAnalyticsPanel } from '../../components/analytics/OwnerAnalyticsPanel'
import { PageHeader } from '../../components/common/Workspace'

const Analytics = () => (
  <div className="cc-page">
    <PageHeader title="Аналитика" description="Тенденции, нагрузка модерации и страницы, где разговор развивается активнее всего." />
    <OwnerAnalyticsPanel />
  </div>
)

export default Analytics
