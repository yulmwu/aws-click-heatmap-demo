/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_AWS_REGION: string
  readonly VITE_ATHENA_WORKGROUP: string
  readonly VITE_GLUE_DATABASE: string
  readonly VITE_GLUE_TABLE: string
  readonly VITE_AWS_ACCESS_KEY_ID: string
  readonly VITE_AWS_SECRET_ACCESS_KEY: string
  readonly VITE_AWS_SESSION_TOKEN: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
