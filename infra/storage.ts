import * as gcp from "@pulumi/gcp";
import * as pulumi from "@pulumi/pulumi";

export function createGcsBucket(name: string) {
    const bucket = new gcp.storage.Bucket(name, {
        location: new pulumi.Config("gcp").require("region"),
        storageClass: "STANDARD",
        uniformBucketLevelAccess: true,
    });

    return bucket;
}