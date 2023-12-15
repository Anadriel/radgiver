import * as gcp from "@pulumi/gcp";
import * as pulumi from "@pulumi/pulumi";

function removeRolePrefix(role: string) {
    return role.startsWith("roles/") ? role.replace("roles/", "") : role;
}

function serviceAccountMember(principal: gcp.serviceaccount.Account | [pulumi.Output<gcp.iam.WorkloadIdentityPool>, string]) {
    if (principal instanceof gcp.serviceaccount.Account) {
        return pulumi.interpolate`serviceAccount:${principal.email}`;
    } else {
        const [poolId, repo] = principal;
        return pulumi.interpolate`principalSet://iam.googleapis.com/${poolId.name}/attribute.repository/${repo}`;
    }
}

function setAsSortedArray(set: Set<string>) {
    const array = Array.from(set);
    array.sort();
    return array;
}

export function createServiceAccount(name: string) {
    const serviceAccount = new gcp.serviceaccount.Account(name, {
        accountId: name,
    });

    return serviceAccount;
}

export function bindServiceAccountToRoles(name: string, principal: gcp.serviceaccount.Account, roles: Set<string>) {

    const iamBindings = setAsSortedArray(roles).map(role => {
        return new gcp.projects.IAMBinding(`${name}-${removeRolePrefix(role)}`, {
            project: new pulumi.Config("gcp").require("project"),
            role: role,
            members: [serviceAccountMember(principal)],
        });
    });

    return iamBindings;
}

export function createServiceAccountIAMBinding(name: string, principal: gcp.serviceaccount.Account | [pulumi.Output<gcp.iam.WorkloadIdentityPool>, string], serviceAccountId: pulumi.Input<string>, roles: Set<string>) {
    const iamBindings = setAsSortedArray(roles).map(role => {
        return new gcp.serviceaccount.IAMBinding(`${name}-${removeRolePrefix(role)}`, {
            serviceAccountId: serviceAccountId,
            role: role,
            members: [serviceAccountMember(principal)],
        });
    });

    return iamBindings;
}

export function createBucketIAMBinding(name: string, principal: gcp.serviceaccount.Account, bucket: gcp.storage.Bucket, roles: Set<string>) {
    const iamBindings = setAsSortedArray(roles).map(role => {
        return new gcp.storage.BucketIAMBinding(`${name}-${removeRolePrefix(role)}`, {
            bucket: bucket.name,
            role: role,
            members: [serviceAccountMember(principal)],
        });
    });

    return iamBindings;
}

export function createSecretManagerIAMBinding(name: string, principal: gcp.serviceaccount.Account, secret: gcp.secretmanager.Secret, roles: Set<string>) {
    const iamBindings = setAsSortedArray(roles).map(role => {
        return new gcp.secretmanager.SecretIamBinding(`${name}-${removeRolePrefix(role)}`, {
            secretId: secret.id,
            role: role,
            members: [serviceAccountMember(principal)],
        });
    });

    return iamBindings;
}