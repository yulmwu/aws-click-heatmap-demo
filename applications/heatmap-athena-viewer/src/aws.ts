import { AthenaClient } from '@aws-sdk/client-athena'
import { S3Client } from '@aws-sdk/client-s3'

type Creds = {
    accessKeyId: string
    secretAccessKey: string
    sessionToken?: string
}

export function makeAthenaClient(region: string, creds: Creds) {
    return new AthenaClient({ region, credentials: creds })
}

export function makeS3Client(region: string, creds: Creds) {
    return new S3Client({ region, credentials: creds })
}
