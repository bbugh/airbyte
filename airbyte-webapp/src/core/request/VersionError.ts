import { ServerError } from './ServerError'

export class VersionError extends ServerError {
  __type = 'version.mismatch'
}

export function isVersionError(error: any): error is VersionError {
  return error.__type === 'version.mismatch'
}
