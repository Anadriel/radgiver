import * as gcp from '@pulumi/gcp';
import * as pulumi from '@pulumi/pulumi';

export function createSecret(name: string) {
    const secret = new gcp.secretmanager.Secret(name, {
        secretId: name,
        replication: {auto: {}},
    });

    return secret;
}

export function createSecretVersion(
    name: string,
    secret: gcp.secretmanager.Secret,
    secretValue: pulumi.Input<string>
) {
    const secretVersion = new gcp.secretmanager.SecretVersion(name, {
        secret: secret.id,
        secretData: secretValue,
    });

    return secretVersion;
}
