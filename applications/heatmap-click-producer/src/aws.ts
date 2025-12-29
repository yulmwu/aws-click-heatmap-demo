import { KinesisClient } from '@aws-sdk/client-kinesis'

type Creds = {
    accessKeyId: string
    secretAccessKey: string
    sessionToken?: string
}

export function makeKinesisClient(region: string, creds: Creds) {
    return new KinesisClient({
        region,
        credentials: creds,
    })
}
