# FTGO Kubernetes Deployment

This directory contains Kubernetes manifests and scripts for deploying the FTGO microservices platform.

## Prerequisites

- kubectl installed
- Either k3s or kind installed for local development
- istioctl (will be installed automatically by setup script)

## Quick Start

### 1. Set up Kubernetes Cluster

```bash
cd deployment/kubernetes
./setup-k8s.sh
```

This script will:
- Create a local Kubernetes cluster (k3s or kind)
- Create the `ftgo-production` namespace with Istio injection enabled
- Install Istio service mesh
- Configure mTLS in STRICT mode
- Apply circuit breaker and retry policies
- Set up RBAC policies

### 2. Verify Setup

```bash
# Check namespace
kubectl get namespace ftgo-production

# Check Istio installation
kubectl get pods -n istio-system

# Check Istio configurations
kubectl get peerauthentication -n ftgo-production
kubectl get destinationrule -n ftgo-production
kubectl get virtualservice -n ftgo-production
```

## Istio Configuration

### mTLS (Mutual TLS)

All service-to-service communication is encrypted with mTLS in STRICT mode:

```yaml
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: ftgo-production
spec:
  mtls:
    mode: STRICT
```

### Circuit Breaker

Circuit breaker configuration (5 consecutive 5xx errors, 30s open state):

```yaml
outlierDetection:
  consecutive5xxErrors: 5
  interval: 30s
  baseEjectionTime: 30s
  maxEjectionPercent: 50
```

### Retry Policy

Retry policy (3 attempts, 500ms base delay, 5xx only):

```yaml
retries:
  attempts: 3
  perTryTimeout: 2s
  retryOn: 5xx,reset,connect-failure,refused-stream
```

### Timeout

Global timeout for all service-to-service requests: 5 seconds

```yaml
timeout: 5s
```

## Service Deployment

Each service has its own directory with:
- `deployment.yaml` - Pod template, replicas, resources
- `service.yaml` - ClusterIP/LoadBalancer
- `configmap.yaml` - Environment-specific config
- `secret.yaml` - Credentials (Vault-injected)

### Deploy a Service

```bash
kubectl apply -f order-service/
```

### Check Service Status

```bash
kubectl get pods -n ftgo-production
kubectl get services -n ftgo-production
kubectl logs -f deployment/ftgo-order-service -n ftgo-production
```

## Scaling

### Manual Scaling

```bash
kubectl scale deployment ftgo-order-service --replicas=5 -n ftgo-production
```

### Horizontal Pod Autoscaler (HPA)

HPA is configured to scale based on CPU utilization > 70%:

```bash
kubectl get hpa -n ftgo-production
```

## Monitoring

### Check Istio Metrics

```bash
# Istio proxy status
istioctl proxy-status -n ftgo-production

# Istio configuration
istioctl analyze -n ftgo-production
```

### View Service Mesh

```bash
# Install Kiali (Istio dashboard)
kubectl apply -f https://raw.githubusercontent.com/istio/istio/release-1.20/samples/addons/kiali.yaml

# Port forward Kiali
kubectl port-forward svc/kiali -n istio-system 20001:20001

# Open http://localhost:20001
```

## Troubleshooting

### Pod not starting

```bash
# Check pod events
kubectl describe pod <pod-name> -n ftgo-production

# Check logs
kubectl logs <pod-name> -n ftgo-production

# Check Istio sidecar logs
kubectl logs <pod-name> -c istio-proxy -n ftgo-production
```

### mTLS issues

```bash
# Check peer authentication
kubectl get peerauthentication -n ftgo-production -o yaml

# Check destination rules
kubectl get destinationrule -n ftgo-production -o yaml

# Verify mTLS is working
istioctl authn tls-check <pod-name> -n ftgo-production
```

### Circuit breaker not working

```bash
# Check destination rule
kubectl get destinationrule ftgo-services -n ftgo-production -o yaml

# Check Envoy stats
kubectl exec <pod-name> -c istio-proxy -n ftgo-production -- curl localhost:15000/stats | grep outlier
```

## Cleanup

### Delete all services

```bash
kubectl delete namespace ftgo-production
```

### Delete Istio

```bash
istioctl uninstall --purge -y
kubectl delete namespace istio-system
```

### Delete cluster (kind only)

```bash
kind delete cluster --name ftgo
```

## Production Deployment

For production deployment:

1. Use a managed Kubernetes service (EKS, GKE, AKS)
2. Configure persistent volumes for databases
3. Set up external load balancer for API Gateway
4. Configure TLS certificates for external traffic
5. Set up monitoring and alerting (Prometheus, Grafana)
6. Configure log aggregation (ELK stack)
7. Set up backup and disaster recovery

## Security

- All service-to-service communication is encrypted with mTLS
- RBAC policies restrict service permissions
- Secrets are managed by Vault (not stored in Kubernetes)
- Network policies can be added for additional isolation

## Resource Limits

Default resource limits per service:

```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "500m"
```

Adjust based on actual usage and load testing results.
