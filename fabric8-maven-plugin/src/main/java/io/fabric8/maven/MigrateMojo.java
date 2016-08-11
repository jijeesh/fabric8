/**
 * Copyright 2005-2016 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven;

import io.fabric8.kubernetes.api.Annotations;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerSpec;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentSpec;
import io.fabric8.kubernetes.api.model.extensions.LabelSelectorBuilder;
import io.fabric8.kubernetes.internal.HasMetadataComparator;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.utils.Files;
import io.fabric8.utils.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Migrates the generated Kubernetes manifest to be used by the 3.x or later of the fabric8-maven-plugin
 */
@Mojo(name = "migrate", requiresDependencyResolution = ResolutionScope.RUNTIME, defaultPhase = LifecyclePhase.INSTALL)
public class MigrateMojo extends AbstractFabric8Mojo {

    /**
     * The output folder for the migrations
     */
    @Parameter(property = "fabric8.migrate.outputDir", defaultValue = "${basedir}/src/main/fabric8")
    protected File outputDir;

    private Map<String, String> kindAliases = new HashMap();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        init();
        File json = getKubernetesJson();
        if (Files.isFile(json)) {

            getLog().info("Kubernetes JSON: " + json);

            try {
                String fileName = json.getName();
                Object dto = KubernetesHelper.loadJson(json);
                if (dto == null) {
                    throw new MojoFailureException("Cannot load kubernetes json: " + json);
                }

                Set<HasMetadata> entities = new TreeSet<>(new HasMetadataComparator());

                ConfigMap parameterConfigMap = null;

                if (dto instanceof Template) {
                    Template template = (Template) dto;

                    List<HasMetadata> objects = template.getObjects();
                    if (objects != null) {
                        entities.addAll(objects);
                    }

                    List<io.fabric8.openshift.api.model.Parameter> parameters = template.getParameters();
                    if (parameters != null && parameters.size() > 0) {
                        parameterConfigMap = new ConfigMapBuilder().withNewMetadataLike(template.getMetadata()).endMetadata().build();

                        Map<String, String> configMapData = new HashMap<>();
                        for (io.fabric8.openshift.api.model.Parameter parameter : parameters) {
                            String name = parameter.getName();
                            String value = parameter.getValue();
                            configMapData.put(convertToConfigMapKey(name), value);
                        }
                        parameterConfigMap.setData(configMapData);

                        migrateEntity(parameterConfigMap, parameterConfigMap);
                        entities.add(parameterConfigMap);
                    }

                    // TODO do something with the parameters!
                } else {
                    entities.addAll(KubernetesHelper.toItemList(dto));
                }


                //Apply all items
                for (HasMetadata entity : entities) {
                    entity = migrateEntity(entity, parameterConfigMap);
                    String name = KubernetesHelper.getName(entity);
                    String kind = shortenKind(KubernetesHelper.getKind(entity).toLowerCase());

                    File outFile = new File(outputDir, name + "-" + kind + ".yml");
                    KubernetesHelper.saveYamlNotEmpty(entity, outFile);

                    getLog().info("Generated migration file: " + outFile);
                }
            } catch (Exception e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

    protected String convertToConfigMapKey(String name) {
        return name.toLowerCase().replace('_', '-');
    }

    /**
     * Returns the migrated entity
     *
     * - use Deployment by default instead of ReplicationController
     * - remove some annotations which should be generated at real build time
     * - replace groupId, artifactId and version with expressions
     */
    protected HasMetadata migrateEntity(HasMetadata entity, ConfigMap parameterConfigMap) {
        migrateMetadata(entity.getMetadata());

        if (entity instanceof ReplicationController) {
            ReplicationController rc = (ReplicationController) entity;
            ReplicationControllerSpec rcSpec = rc.getSpec();

            Deployment deployment = new Deployment();
            deployment.setMetadata(entity.getMetadata());
            if (rcSpec != null) {
                DeploymentSpec deploymentSpec = new DeploymentSpec();

                Map<String, String> selector = rcSpec.getSelector();
                if (selector != null) {
                    selector = new LinkedHashMap<>(selector);
                    // Deployment's selector should not have a version as we use that for each RC / RS
                    selector.remove("version");
                }
                migrateLabels(selector);
                deploymentSpec.setReplicas(rcSpec.getReplicas());
                if (selector != null) {
                    deploymentSpec.setSelector(new LabelSelectorBuilder().withMatchLabels(selector).build());
                }
                PodTemplateSpec podTemplateSpec = rcSpec.getTemplate();
                if (podTemplateSpec != null) {
                    PodSpec podSpec = podTemplateSpec.getSpec();
                    if (podSpec != null) {
                        List<Container> containers = podSpec.getContainers();
                        if (containers != null) {
                            for (Container container : containers) {
                                migrateContainer(container, parameterConfigMap);
                            }
                        }
                    }
                }

                PodTemplateSpec template = rcSpec.getTemplate();
                if (template != null) {
                    migrateMetadata(template.getMetadata());
                    deploymentSpec.setTemplate(template);
                }
                deployment.setSpec(deploymentSpec);
            }
            return deployment;
        }
        return entity;
    }

    protected void migrateContainer(Container container, ConfigMap parameterConfigMap) {
        String image = container.getImage();
        if (image != null) {
            MavenProject project = getProject();
            String version = project.getVersion();
            if (version != null) {
                String label = ":" + version;
                if (image.endsWith(label)) {
                    image = Strings.stripSuffix(image, label) + ":${project.version}";
                    container.setImage(image);
                } else {
                    getLog().warn("Image does not end with " + label + " as image is: " + image);
                }
            }
        }
        if (parameterConfigMap != null) {
            Map<String, String> parameters = parameterConfigMap.getData();
            if (parameters != null) {
                String configMapName = KubernetesHelper.getName(parameterConfigMap);
                List<EnvVar> env = container.getEnv();
                for (EnvVar envVar : env) {
                    String value = envVar.getValue();
                    if (value != null) {
                        String name = envVar.getName();
                        if (value.startsWith("${") && value.endsWith("}")) {
                            String variableName = Strings.stripPrefix(Strings.stripSuffix(name, "}"), "${");
                            String expression = convertToConfigMapKey(variableName);
                            if (parameters.containsKey(expression)) {
                                // lets switch to a refer to the config map!
                                envVar.setValue(null);
                                envVar.setValueFrom(new EnvVarSourceBuilder().
                                        withNewConfigMapKeyRef().withName(configMapName).withKey(expression).endConfigMapKeyRef().build());
                            }

                        }
                    }
                }
            }
        }
    }

    protected void migrateMetadata(ObjectMeta metadata) {
        if (metadata == null) {
            return;
        }
        Map<String, String> annotations = metadata.getAnnotations();
        if (annotations != null) {
            annotations.remove(Annotations.Builds.BUILD_ID);
            annotations.remove(Annotations.Builds.BUILD_URL);
            annotations.remove(Annotations.Builds.GIT_BRANCH);
            annotations.remove(Annotations.Builds.GIT_COMMIT);
            annotations.remove(Annotations.Builds.GIT_URL);
        }
        migrateLabels(metadata.getLabels());
    }

    private void migrateLabels(Map<String, String> labels) {
        if (labels != null) {
            MavenProject project = getProject();

            // TODO there is a currently a bug when using values other than ${project.artifactId} in fabric8-maven-plugin 3.x
            // where it overrides the value of the sepc.selector.matchLabels to the artifactId which then breaks
            // as the template.metadata.labels.project will differ
            boolean alwaysUseArtifactIdForProject = true;
            if (alwaysUseArtifactIdForProject) {
                if (labels.containsKey("project")) {
                    labels.put("project", "${project.artifactId}");
                }
            } else {
                replaceKeyValueWith(labels, "project", project.getArtifactId(), "${project.artifactId}");
            }
            replaceKeyValueWith(labels, "version", project.getVersion(), "${project.version}");
        }
    }

    private void replaceKeyValueWith(Map<String, String> labels, String key, String oldValue, String newValue) {
        String value = labels.get(key);
        if (Objects.equals(value, oldValue)) {
            labels.put(key, newValue);
        }
    }

    private void init() {
        kindAliases.put("configmap", "cm");
        kindAliases.put("deploymentconfig", "dc");
        kindAliases.put("replicationcontroller", "rc");
        kindAliases.put("replicaset", "rs");
        kindAliases.put("service", "svc");
        kindAliases.put("serviceaccount", "sa");
    }


    private String shortenKind(String kind) {
        String answer = kindAliases.get(kind);
        if (answer == null) {
            return kind;
        }
        return answer;
    }
}
