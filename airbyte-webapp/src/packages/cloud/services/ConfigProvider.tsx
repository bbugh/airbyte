import React from 'react'

import {
  Config,
  ConfigServiceProvider,
  ValueProvider,
  envConfigProvider,
  windowConfigProvider,
} from '@app/config'

import {
  cloudEnvConfigProvider,
  fileConfigProvider,
  defaultConfig,
} from './config'

const configProviders: ValueProvider<Config> = [
  fileConfigProvider,
  cloudEnvConfigProvider,
  windowConfigProvider,
  envConfigProvider,
]

/**
 * This Provider is responsible for injecting config in context and loading
 * all required subconfigs if necessary
 */
const ConfigProvider: React.FC = ({ children }) => (
  <ConfigServiceProvider
    defaultConfig={defaultConfig}
    providers={configProviders}
  >
    {children}
  </ConfigServiceProvider>
)

export { ConfigProvider }
