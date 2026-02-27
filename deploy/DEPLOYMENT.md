# LogHealer - DigitalOcean Deployment Guide

## Prerequisites

- DigitalOcean account
- Domain name (e.g., `loghealer.reddia-x.com`)
- GitHub repository with this code

## Step 1: Create DigitalOcean Droplet

1. Go to [DigitalOcean](https://cloud.digitalocean.com)
2. Create a new Droplet:
   - **Image**: Ubuntu 24.04 LTS
   - **Size**: Basic, 4GB RAM / 2 vCPUs ($24/month) - minimum for Elasticsearch
   - **Region**: Frankfurt (fra1) or closest to your users
   - **Authentication**: SSH Key (recommended)

3. Note the Droplet IP address

## Step 2: Configure DNS

Add DNS A record:
```
Type: A
Name: loghealer (or @ for root)
Value: YOUR_DROPLET_IP
TTL: 300
```

## Step 3: Server Setup

SSH into your droplet:
```bash
ssh root@YOUR_DROPLET_IP
```

Run the setup script:
```bash
# Update system
apt update && apt upgrade -y

# Install Docker
curl -fsSL https://get.docker.com | sh

# Install Docker Compose
apt install docker-compose-plugin -y

# Create app directory
mkdir -p /opt/loghealer
cd /opt/loghealer

# Create .env file
cat > .env << 'EOF'
DATABASE_PASSWORD=GENERATE_SECURE_PASSWORD_HERE
DOMAIN=loghealer.reddia-x.com
DOCKER_REGISTRY=ghcr.io/YOUR_GITHUB_USERNAME
VERSION=latest
EOF

# Generate secure password
echo "DATABASE_PASSWORD=$(openssl rand -base64 32)" > .env
```

## Step 4: Copy Production Files

From your local machine:
```bash
scp docker-compose.prod.yml root@YOUR_DROPLET_IP:/opt/loghealer/
scp Caddyfile root@YOUR_DROPLET_IP:/opt/loghealer/
```

## Step 5: Configure GitHub Secrets

In your GitHub repository, go to **Settings > Secrets and variables > Actions**

Add these secrets:
| Secret Name | Value |
|------------|-------|
| `DROPLET_HOST` | Your Droplet IP |
| `DROPLET_USERNAME` | `root` |
| `DROPLET_SSH_KEY` | Your private SSH key |

## Step 6: Initial Deployment

On the server:
```bash
cd /opt/loghealer

# Login to GitHub Container Registry
echo $GITHUB_TOKEN | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin

# Start services
docker compose -f docker-compose.prod.yml up -d

# Check logs
docker compose -f docker-compose.prod.yml logs -f
```

## Step 7: Verify Deployment

1. Open `https://loghealer.reddia-x.com` in browser
2. Check health endpoint: `https://loghealer.reddia-x.com/actuator/health`

## Useful Commands

```bash
# View logs
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml logs -f dashboard

# Restart services
docker compose -f docker-compose.prod.yml restart

# Stop everything
docker compose -f docker-compose.prod.yml down

# Update and restart
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d

# Check resource usage
docker stats

# Access PostgreSQL
docker compose -f docker-compose.prod.yml exec postgres psql -U loghealer -d rdx_loghealer_db

# Access Elasticsearch
curl http://localhost:9200/_cluster/health?pretty
```

## Monitoring & Logs

### View application logs:
```bash
docker compose -f docker-compose.prod.yml logs -f --tail=100 backend
```

### Check Elasticsearch indices:
```bash
curl http://localhost:9200/_cat/indices?v
```

### Backup PostgreSQL:
```bash
docker compose -f docker-compose.prod.yml exec postgres pg_dump -U loghealer rdx_loghealer_db > backup.sql
```

## Estimated Monthly Cost

| Service | Cost |
|---------|------|
| Droplet (4GB RAM) | $24 |
| **Total** | **$24/month** |

For higher traffic, consider:
- Managed PostgreSQL ($15/month)
- Managed Elasticsearch via Elastic Cloud (~$50/month)
- Load Balancer ($12/month)

## Troubleshooting

### Elasticsearch won't start
```bash
# Increase virtual memory
sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" >> /etc/sysctl.conf
```

### Out of disk space
```bash
# Clean Docker
docker system prune -af
docker volume prune -f
```

### SSL certificate issues
Caddy automatically handles Let's Encrypt. If issues:
```bash
docker compose -f docker-compose.prod.yml logs caddy
```
