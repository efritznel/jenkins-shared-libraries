#!/usr/bin/env groovy

/**
 * Update Kubernetes manifests with new image tags
 */
def call(Map config = [:]) {
    def imageTag       = config.imageTag ?: error("Image tag is required")
    def manifestsPath  = config.manifestsPath ?: 'kubernetes'
    def gitCredentials = config.gitCredentials ?: 'github-credentials'
    def gitUserName    = config.gitUserName ?: 'Jenkins CI'
    def gitUserEmail   = config.gitUserEmail ?: 'jenkins@example.com'
    def gitBranch      = config.gitBranch ?: 'main'   // optional override

    echo "Updating Kubernetes manifests with image tag: ${imageTag}"

    withCredentials([usernamePassword(
        credentialsId: gitCredentials,
        usernameVariable: 'GIT_USERNAME',
        passwordVariable: 'GIT_PASSWORD'
    )]) {
        sh """#!/bin/bash
            set -euo pipefail

            # Configure Git identity (for commits)
            git config user.name "${gitUserName}"
            git config user.email "${gitUserEmail}"

            # Update main application deployment
            sed -i "s|image: efritznel/easyshop-app:.*|image: trainwithshubham/easyshop-app:${imageTag}|g" ${manifestsPath}/08-easyshop-deployment.yaml

            # Update migration job if it exists
            if [ -f "${manifestsPath}/12-migration-job.yaml" ]; then
              sed -i "s|image: efritznel/easyshop-migration:.*|image: trainwithshubham/easyshop-migration:${imageTag}|g" ${manifestsPath}/12-migration-job.yaml
            fi

            # Ensure ingress is using the correct domain
            if [ -f "${manifestsPath}/10-ingress.yaml" ]; then
              sed -i "s|host: .*|host: easyshop.letsdeployit.com|g" ${manifestsPath}/10-ingress.yaml
            fi

            # If nothing changed, stop
            if git diff --quiet; then
              echo "No changes to commit"
              exit 0
            fi

            # Commit changes
            git add ${manifestsPath}/*.yaml
            git commit -m "Update image tags to ${imageTag} and ensure correct domain [ci skip]"

            # Use the origin already configured in the workspace; inject credentials for push
            ORIGIN_URL=\$(git config --get remote.origin.url)

            # Normalize to HTTPS (in case it's git@github.com:...)
            if echo "\$ORIGIN_URL" | grep -q '^git@github.com:'; then
              ORIGIN_URL="https://github.com/\${ORIGIN_URL#git@github.com:}"
            fi

            # Inject credentials into the URL
            AUTH_URL="https://\${GIT_USERNAME}:\${GIT_PASSWORD}@\${ORIGIN_URL#https://}"

            git remote set-url origin "\$AUTH_URL"
            git push origin HEAD:${gitBranch}
        """
    }
}
