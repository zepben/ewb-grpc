packer {
  required_plugins {
    docker = {
      source  = "github.com/hashicorp/docker"
      version = "~> 1"
    }
  }
}

variable "tag" {
    type = string
    default = "1.0.0"
}

variable "registry_token" {
  type    = string
  default = "${env("REGISTRY_ACCESS_TOKEN")}"
}

variable "registry_user" {
  type    = string
  default = "${env("REGISTRY_USER")}"
}

variable "repository" {
  type = string
  default = "${env("REGISTRY")}/zepben/grpc-c-builder"
}

source "docker" "image" {
  commit = "true"
  image  = "alpine:3.20"
}

build {
  sources = ["source.docker.image"]

  provisioner "shell" {
    inline = ["apk add grpc protoc protobuf-c-compiler make"]
  }

  post-processors {
    post-processor "docker-tag" {
      name       = "docker.tag"
      repository = "${var.repository}"
      tags       = [${var.tag}]
    }
    post-processor "docker-push" {
      name           = "docker.push"
      login           = true
      login_password = "${var.registry_token}"
      login_username = "${var.registry_user}"
    }
  }
}
