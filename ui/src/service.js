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
import { h } from 'vue'
import { VBtn, VChip, VIcon, useI18nStore } from '@ligoj/host'

const PARAM_SITE = 'service:build:travis:url-site'
const PARAM_JOB = 'service:build:travis:job'

/** Link to the Travis job. Mirrors `renderServiceLink('home', urlSite + job)`. */
function renderFeatures(subscription) {
  const params = subscription?.parameters
  const site = params?.[PARAM_SITE]
  const job = params?.[PARAM_JOB]
  if (!site || !job) return []
  const { t } = useI18nStore()
  return [
    h(
      VBtn,
      {
        icon: true,
        size: 'small',
        variant: 'text',
        href: `${site.replace(/\/$/, '')}/${job}`,
        target: '_blank',
        rel: 'noopener noreferrer',
        title: t('service:build:travis:job'),
      },
      () => h(VIcon, { size: 'small' }, () => 'mdi-home'),
    ),
  ]
}

/** Job-name chip. Mirrors `renderKey('service:build:travis:job')`. */
function renderDetailsKey(subscription) {
  const job = subscription?.parameters?.[PARAM_JOB]
  if (!job) return null
  const { t } = useI18nStore()
  return h(
    VChip,
    { size: 'small', variant: 'tonal', class: 'mr-1', title: t('service:build:travis:job') },
    () => [h(VIcon, { start: true, size: 'small' }, () => 'mdi-cog-outline'), ' ', String(job)],
  )
}

export default { renderFeatures, renderDetailsKey }
