packer {
  required_plugins {
    docker = {
      source  = "github.com/hashicorp/docker"
      version = "~> 1"
    }
  }
}

variable "tags" {
    type = list(string)
    default = ["1.0.0"]
}

variable "dockerhub_pw" {
  type    = string
  default = "${env("DOCKER_HUB_ACCESS_TOKEN")}"
}

variable "dockerhub_user" {
  type    = string
  default = "${env("DOCKER_HUB_USER")}"
}

variable "repository" {
  type = string
  default = "zepben/grpc-c-builder"
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
      tags       = var.tags
    }
    post-processor "docker-push" {
      name           = "docker.push"
      login           = true
      login_password = "${var.dockerhub_pw}"
      login_username = "${var.dockerhub_user}"
    }
  }
}
