## Fabric8 Arquillian 

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.fabric8/fabric8-arquillian/badge.svg?style=flat-square)](https://maven-badges.herokuapp.com/maven-central/io.fabric8/fabric8-arquillian/)
[![Javadocs](http://www.javadoc.io/badge/io.fabric8/fabric8-arquillian.svg?color=blue)](http://www.javadoc.io/doc/io.fabric8/fabric8-arquillian)

This library provides an [Arquillian](http://arquillian.org/) plugin for [integration testing](http://fabric8.io/guide/testing.html) your [Apps](http://fabric8.io/guide/apps.html) on top of Kubernetes; using Kubernetes to provision and orchestrate the containers and then making [assertions](https://github.com/fabric8io/fabric8/tree/master/components/kubernetes-assertions) that the required resources startup correctly.

### Example

There are lots of examples in the [quickstarts](https://github.com/fabric8-quickstarts).

Here is an [example Arquillian Fabric8 integration test](https://github.com/fabric8-quickstarts/spring-boot-camel/blob/master/src/test/java/io/fabric8/tests/integration/KubernetesIntegrationKT.java)

### Assertion libraries

The following libraries are provided to help you create concise assertions using [assertj](http://joel-costigliola.github.io/assertj/) based DSL:

 * [kubernetes-assertions](https://github.com/fabric8io/fabric8/tree/master/components/kubernetes-assertions) provides a set of [assertj](http://joel-costigliola.github.io/assertj/) assertions of the form **assertThat(kubernetesResource)** for working with the [kubernetes-api](https://github.com/fabric8io/fabric8/tree/master/components/kubernetes-api)
 * [jolokia-assertions](https://github.com/fabric8io/fabric8/tree/master/components/jolokia-assertions) makes it easy to perform assertions on remote JVMs via JMX using  [Jolokia](http://jolokia.org/) over HTTP/JSON
 * [kubernetes-jolokia](https://github.com/fabric8io/fabric8/tree/master/components/kubernetes-jolokia) makes it easy to work with the [Jolokia Client API](http://jolokia.org/reference/html/clients.html#client-java) and Java containers running in [Pods](http://fabric8.io/guide/pods.html) inside Kubernetes which expose the Jolokia port

### Add it to your Maven pom.xml

To be able to use this library add this to your [Apache Maven](http://maven.apache.org/) based project add this into your pom.xml

            <dependency>
                <groupId>io.fabric8</groupId>
                <artifactId>fabric8-arquillian</artifactId>
                <version>2.2.184</version>
                <scope>test</scope>
            </dependency>


