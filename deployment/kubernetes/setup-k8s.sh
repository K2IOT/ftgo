#!/bin/bash

set -e

echo "Setting up FTGO Kubernetes cluster..."

# Check if kubectl is installed
if ! command -v kubectl &> /dev/null; then
    echo "kubectl is not installed. Please install kubectl first."
    exit 1
fi

# Check if k3s or kind is available
if command -v k3s &> /dev/null; then
    echo "Using k3s..."
    CLUSTER_TYPE="k3s"
elif command -v kind &> /dev/null; then
    echo "Using kind..."
    CLUSTER_TYPE="kind"
    
    # Create kind cluster if it doesn't exist
    if ! kind get clusters | grep -q "ftgo"; then
        echo "Creating kind cluster..."
        cat <<EOF | kind create cluster --name ftgo --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
- role: worker
- role: worker
EOF
    fi
else
    echo "Neither k3s nor kind is installed. Please install one of them."
    echo "  - k3s: curl -sfL https://get.k3s.io | sh -"
    echo "  - kind: https://kind.sigs.k8s.io/docs/user/quick-start/#installation"
    exit 1
fi

# Create namespace
echo "Creating ftgo-production namespace..."
kubectl apply -f namespace.yaml

# Install Istio
echo "Installing Istio..."
if ! command -v istioctl &> /dev/null; then
    echo "istioctl is not installed. Installing Istio..."
    curl -L https://istio.io/downloadIstio | ISTIO_VERSION=1.20.0 sh -
    export PATH=$PWD/istio-1.20.0/bin:$PATH
fi

# Install Istio with default profile
istioctl install --set profile=default -y

# Wait for Istio to be ready
echo "Waiting for Istio to be ready..."
kubectl wait --for=condition=available --timeout=300s deployment/istiod -n istio-system

# Apply Istio configurations
echo "Applying Istio configurations..."
kubectl apply -f istio/peer-authentication.yaml
kubectl apply -f istio/destination-rule.yaml
kubectl apply -f istio/virtual-service.yaml

# Apply RBAC
echo "Applying RBAC policies..."
kubectl apply -f rbac.yaml

echo ""
echo "Kubernetes cluster setup complete!"
echo ""
echo "Cluster type: $CLUSTER_TYPE"
echo "Namespace: ftgo-production"
echo "Istio mTLS: STRICT mode enabled"
echo ""
echo "Next steps:"
echo "  1. Deploy services: kubectl apply -f order-service/"
echo "  2. Check pods: kubectl get pods -n ftgo-production"
echo "  3. Check Istio: kubectl get peerauthentication -n ftgo-production"
