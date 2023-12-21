import * as pulumi from '@pulumi/pulumi';
import * as gcp from '@pulumi/gcp';

const gcpConfig = new pulumi.Config('gcp');
const gcpProject = gcpConfig.require('project');
const gcpRegion = gcpConfig.require('region');

const env = pulumi.getStack();
const infra = new pulumi.StackReference(`ferm-org/radgiver-infra/${env}`);

const containerVersion = process.env.CONTAINER_VERSION;

const radgiverService = new gcp.cloudrunv2.Service('radgiver-service', {
    location: gcpRegion, // Change to the desired location
    project: gcpProject, // Provide your GCP project ID here
    ingress: 'INGRESS_TRAFFIC_ALL',
    traffics: [
        {
            percent: 100,
            type: 'TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST',
        },
    ],
    template: {
        executionEnvironment: 'EXECUTION_ENVIRONMENT_GEN2',
        maxInstanceRequestConcurrency: 80,
        scaling: {
            minInstanceCount: 0,
            maxInstanceCount: 1,
        },
        serviceAccount: infra.getOutput('appServiceAccountEmail'),
        timeout: '3600s',
        containers: [
            {
                image: pulumi.interpolate`${gcpRegion}-docker.pkg.dev/${gcpProject}/${infra.getOutput(
                    'artifactRegistryName'
                )}/radgiver-service:${containerVersion}`,
                ports: [
                    {
                        containerPort: 8080,
                    },
                ],
            },
        ],
    },
});

new gcp.cloudrunv2.ServiceIamBinding('radgiver-service-iam-binding', {
    project: gcpProject,
    location: gcpRegion,
    name: radgiverService.name,
    members: ['allUsers'],
    role: 'roles/run.invoker',
});

export const radgiverServiceUrl = radgiverService.uri;
