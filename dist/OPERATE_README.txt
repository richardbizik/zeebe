This is a distribution of Operate ${project.version}

How to run
==========

Prerequisites:
1. Zeebe of version ${project.version} is running with Elasticsearch exporter turned on. See https://zeebe.io/ on how to install and run Zeebe.
2. Elasticsearch of version ${version.elasticsearch} or higher is running: it can be either the same instance configured for Zeebe export, or a separate one. See https://www.elastic.co/products/elasticsearch on how to install and run Elasticsearch

To run Operate:
1. Adjust Operate configuration file config/application.yml to point to your Zeebe and Elasticsearch instances
2. Run bin/operate or bin/operate.bat, depending on your file system.