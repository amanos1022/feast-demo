#!/usr/bin/env bash
kubectl port-forward svc/seaweedfs-s3 -n data 9000:8333 &
kubectl port-forward svc/seaweedfs-filer -n data 8888:8888 &
kubectl port-forward svc/music-kafka-external-bootstrap -n data 9094:9094 &
kubectl port-forward svc/seaweedfs-master -n data 9333:9333 &
kubectl port-forward svc/postgres -n data 5432:5432 &
kubectl port-forward svc/redis -n data 6379:6379
