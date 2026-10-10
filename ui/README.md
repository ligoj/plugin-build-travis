# plugin-build-travis — Vue UI

Tool plugin (`service:build:travis`), the Travis CI implementation of the
`build` service. Compiled to `webjars/build-travis/vue/`.

Ships i18n parameter labels + `renderFeatures` (Travis job link
`url-site/<job>`), `renderDetailsKey` (job chip) and `parameterField` (the
subscribe wizard's job search). `requires: ['build']`;
the parent merges via its delegation hook. The legacy build-now button and
live status icon (read `subscription.data.job`) are deferred.

```bash
npm install && npm run build && npm run lint && npm test
```
