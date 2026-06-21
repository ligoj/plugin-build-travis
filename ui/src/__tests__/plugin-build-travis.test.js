import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useI18nStore } from '@ligoj/host'
import def from '../index.js'

beforeEach(() => { setActivePinia(createPinia()) })

describe('plugin-build-travis contract', () => {
  it('exposes a valid tool-level manifest', () => {
    expect(def.id).toBe('build-travis')
    expect(def.requires).toEqual(['build'])
    expect(def.routes).toBeUndefined()
    expect(def.meta).toMatchObject({ icon: expect.any(String), color: expect.any(String) })
  })
  it('merges i18n on install', () => {
    const i18n = useI18nStore()
    def.install()
    expect(i18n.t('service:build:travis:job')).toBe('Job')
    expect(i18n.t('service:build:travis:url-site')).toBe('Site URL')
  })
  it('throws for an unknown feature', () => {
    expect(() => def.feature('nope')).toThrow(/no feature "nope"/)
  })
  it('renderFeatures returns the Travis job link when url-site + job are set', () => {
    def.install()
    const vnodes = def.feature('renderFeatures', {
      parameters: { 'service:build:travis:url-site': 'https://travis.example.org', 'service:build:travis:job': 'org/repo' },
    })
    expect(vnodes).toHaveLength(1)
    expect(vnodes[0].props.href).toBe('https://travis.example.org/org/repo')
    expect(vnodes[0].props.target).toBe('_blank')
  })
  it('renderFeatures returns [] without url-site or job', () => {
    def.install()
    expect(def.feature('renderFeatures', { parameters: { 'service:build:travis:job': 'x' } })).toEqual([])
    expect(def.feature('renderFeatures', {})).toEqual([])
  })
  it('renderDetailsKey returns the job chip when present', () => {
    def.install()
    expect(def.feature('renderDetailsKey', { parameters: { 'service:build:travis:job': 'org/repo' } })).toBeTruthy()
    expect(def.feature('renderDetailsKey', { parameters: {} })).toBeNull()
  })
})
