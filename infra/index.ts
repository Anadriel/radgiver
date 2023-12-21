import * as pulumi from '@pulumi/pulumi';
import * as gcp from '@pulumi/gcp';
import * as iam from './iam';
import * as storage from './storage';
import * as secret from './secret';

const gcpConfig = new pulumi.Config('gcp');
const gcpRegion = gcpConfig.require('region');
const githubRepo = 'ferm-org/radgiver';

const telegramBotToken = process.env.TELEGRAM_BOT_TOKEN;

// ------------------------------
// Workload Identity Federation Pool
const sharedWorkloadIdentityPool = pulumi
    .output(gcp.iam.WorkloadIdentityPool.get('wif-pool', 'gha-pool'))
    .apply(pool =>
        pool
            ? pool
            : new gcp.iam.WorkloadIdentityPool('wif-pool', {
                  workloadIdentityPoolId: 'gha-pool',
              })
    );

// Workload Identity Federation Provider for GitHub
const githubIdentityPoolProvider = pulumi
    .output(
        gcp.iam.WorkloadIdentityPoolProvider.get(
            'wif-pool-provider-github',
            pulumi.interpolate`${sharedWorkloadIdentityPool.name}/providers/github-provider`
        )
    )
    .apply(provider =>
        provider
            ? provider
            : new gcp.iam.WorkloadIdentityPoolProvider(
                  'wif-pool-provider-github',
                  {
                      workloadIdentityPoolId: sharedWorkloadIdentityPool.id,
                      workloadIdentityPoolProviderId: 'github-provider',
                      attributeMapping: {
                          'google.subject': 'assertion.sub',
                          'attribute.actor': 'assertion.actor',
                          'attribute.repository': 'assertion.repository',
                      },
                      oidc: {
                          issuerUri:
                              'https://token.actions.githubusercontent.com',
                      },
                  }
              )
    );

// Artifact registry for storing docker images
const artifactRegistry = new gcp.artifactregistry.Repository(
    'artifact-registry',
    {
        location: gcpRegion,
        repositoryId: 'artifact-registry',
        format: 'DOCKER',
        description: 'Artifact repository for Docker images',
        cleanupPolicies: [
            {
                id: 'delete-untagged-images-after-1-day',
                action: 'DELETE',
                condition: {
                    olderThan: '86400s', // 1 day in seconds
                    tagState: 'UNTAGGED',
                },
            },
            {
                id: 'delete-snapshot-images-after-7-days',
                action: 'DELETE',
                condition: {
                    olderThan: '604800s', // 7 days in seconds
                    tagState: 'TAGGED',
                    tagPrefixes: ['snapshot-'],
                },
            },
            {
                id: 'keep-25-most-recent-images',
                mostRecentVersions: {
                    keepCount: 25,
                },
                action: 'KEEP',
            },
        ],
    }
);

// ------------------------------
// Service account to be used by the application itself
const appServiceAccount = iam.createServiceAccount('app-sa');

// GCS bucket for application data
const appBucket = storage.createGcsBucket('app-bucket');

// Secret manager secret for the telegram bot token
const telegramBotTokenSecret = secret.createSecret('telegram-bot-token');
telegramBotToken
    ? secret.createSecretVersion(
          'telegram-bot-token-version',
          telegramBotTokenSecret,
          telegramBotToken
      )
    : undefined;

iam.createSecretManagerIAMBinding(
    'app-sa-telegram-bot-token-access',
    appServiceAccount,
    telegramBotTokenSecret,
    new Set(['roles/secretmanager.secretAccessor'])
);

// Allow the app to read/write to the app bucket
iam.createBucketIAMBinding(
    'app-sa-app-bucket-access',
    appServiceAccount,
    appBucket,
    new Set(['roles/storage.objectAdmin'])
);

// Allow the app to access the artifact registry and use Vertex AI
iam.bindServiceAccountToRoles(
    'app-sa-bindings',
    appServiceAccount,
    new Set(['roles/artifactregistry.reader', 'roles/aiplatform.user'])
);

// ------------------------------
// Service account to be used by the CI pipeline
const ghaCiServiceAccount = iam.createServiceAccount('gha-ci-sa');

// Allow CI to use Vertex AI
iam.bindServiceAccountToRoles(
    'gha-ci-sa-bindings',
    ghaCiServiceAccount,
    new Set(['roles/aiplatform.user'])
);

// Allow Identities from the GitHub WIF pool to impersonate the CI service account
iam.createServiceAccountIAMBinding(
    'gha-ci-sa-wif-binding',
    [sharedWorkloadIdentityPool, githubRepo],
    ghaCiServiceAccount.id,
    new Set(['roles/iam.workloadIdentityUser'])
);

// ------------------------------
// Service account to be used by the CD pipeline
const ghaCdServiceAccount = iam.createServiceAccount('gha-cd-sa');

// Allow CD to push to the artifact registry and to deploy cloud run revisions
iam.bindServiceAccountToRoles(
    'gha-cd-sa-bindings',
    ghaCdServiceAccount,
    new Set([
        'roles/artifactregistry.writer',
        'roles/run.admin',
        'roles/iam.serviceAccountUser',
    ])
);

// Allow Identities from the GitHub WIF pool to impersonate the CD service account
iam.createServiceAccountIAMBinding(
    'gha-cd-sa-wif-binding',
    [sharedWorkloadIdentityPool, githubRepo],
    ghaCdServiceAccount.id,
    new Set(['roles/iam.workloadIdentityUser'])
);

// ------------------------------
// Service account to be used by the infrastructure pipeline
const ghaInfraServiceAccount = iam.createServiceAccount('gha-infra-sa');

// Allow infrastructure pipeline to edit resources in the project
iam.bindServiceAccountToRoles(
    'gha-infra-sa-bindings',
    ghaInfraServiceAccount,
    new Set(['roles/editor', 'roles/resourcemanager.projectIamAdmin'])
);

// Allow Identities from the GitHub WIF pool to impersonate the CD service account
iam.createServiceAccountIAMBinding(
    'gha-infra-sa-wif-binding',
    [sharedWorkloadIdentityPool, githubRepo],
    ghaInfraServiceAccount.id,
    new Set(['roles/iam.workloadIdentityUser'])
);

export const artifactRegistryName = artifactRegistry.name;
export const appBucketName = appBucket.name;
export const appServiceAccountEmail = appServiceAccount.email;
export const ghaCiServiceAccountEmail = ghaCiServiceAccount.email;
export const ghaCdServiceAccountEmail = ghaCdServiceAccount.email;
export const ghaInfraServiceAccountEmail = ghaInfraServiceAccount.email;
export const workflowIdentityPoolId = sharedWorkloadIdentityPool.id;
export const githubIdentityPoolProviderId = githubIdentityPoolProvider.name;
