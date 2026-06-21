/*
 * Service layer for plugin "build-travis".
 *
 * Tool-level plugin (`service:build:travis`). The parent `plugin-build`
 * delegates the subscription-row hooks to us. Mirrors `travis.js`:
 *   - renderFeatures   → a link to the Travis job (url-site + job).
 *   - renderDetailsKey → the job-name chip.
 *
 * The legacy `renderDetailsFeatures` status icon and the build-now button
 * read live `subscription.data.job` — that live-data surface is deferred
 * (as for jenkins). Kept free of Vue SFC imports for unit testing.
 */
import { renderServiceLink, renderDetailsChip, useI18nStore } from '@ligoj/host'

const PARAM_SITE = 'service:build:travis:url-site'
const PARAM_JOB = 'service:build:travis:job'

/** Link to the Travis job. Mirrors `renderServiceLink('home', urlSite + job)`. */
function renderFeatures(subscription) {
  const params = subscription?.parameters
  const site = params?.[PARAM_SITE]
  const job = params?.[PARAM_JOB]
  if (!site || !job) return []
  const { t } = useI18nStore()
  return [renderServiceLink({ icon: 'mdi-home', href: `${site.replace(/\/$/, '')}/${job}`, title: t('service:build:travis:job') })]
}

/** Job-name chip. Mirrors `renderKey('service:build:travis:job')`. */
function renderDetailsKey(subscription) {
  const job = subscription?.parameters?.[PARAM_JOB]
  if (!job) return null
  const { t } = useI18nStore()
  return renderDetailsChip({ icon: 'mdi-cog-outline', text: job, title: t('service:build:travis:job') })
}

export default { renderFeatures, renderDetailsKey }
