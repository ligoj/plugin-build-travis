/*
 * Plugin "build-travis" — Travis CI implementation of plugin-build.
 *
 * Tool-level plugin (`service:build:travis`). Augments the parent
 * `plugin-build` via i18n parameter labels + row features (Travis job
 * link, job chip) merged in through plugin-build's `subPluginIdFor`
 * delegation hook. Authored as source — compiled to
 * `/main/build-travis/vue/index.js` by Vite.
 */
import { useI18nStore } from '@ligoj/host'
import enMessages from './i18n/en.js'
import frMessages from './i18n/fr.js'
import service from './service.js'

const features = {
  renderFeatures: service.renderFeatures,
  renderDetailsKey: service.renderDetailsKey,
}

export default {
  id: 'build-travis',
  label: 'Travis CI',
  requires: ['build'],
  install() {
    const i18n = useI18nStore()
    i18n.merge(enMessages, 'en')
    i18n.merge(frMessages, 'fr')
  },
  feature(action, ...args) {
    const fn = features[action]
    if (!fn) throw new Error(`Plugin "build-travis" has no feature "${action}"`)
    return fn(...args)
  },
  service,
  meta: { icon: 'mdi-bus', color: 'red-darken-1' },
}

export { service }
