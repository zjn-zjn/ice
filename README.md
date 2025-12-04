<img width="128" alt="5" src="https://user-images.githubusercontent.com/33447125/151098049-72aaf8d1-b759-4d84-bf6b-1a2260033582.png">

> 致力于解决灵活繁复的硬编码问题
> 
> Committed to solving flexible and complex hard-coded problems

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![Badge](https://img.shields.io/badge/link-ice--docs-brightgreen)](https://waitmoon.com/)

- **文档链接**
    - [**首页**](https://waitmoon.com/)
    - [**快速开始**](https://waitmoon.com/guide/getting-started.html)
    - [**视频介绍**](https://www.bilibili.com/video/BV1hg411A7jx)
    - [**交流群**](https://waitmoon.com/community/community.html)
    - [**开发&配置视频**](https://www.bilibili.com/video/BV1Q34y1R7KF)
    - [**配置体验(ice-test&ice-server真实部署)**](https://eg.waitmoon.com/)
  

- **Doc links**
    - [**Home Page**](https://waitmoon.com/en/)
    - [**Quick start**](https://waitmoon.com/en/guide/getting-started.html)
    - [**Community**](https://waitmoon.com/en/community/community.html)
    - [**Configuration experience(ice-test&ice-server real deployment)**](https://eg.waitmoon.com/)

## Docker 部署 / Docker Deployment

```bash
# 拉取镜像 / Pull image
docker pull waitmoon/ice-server:latest

# 运行 / Run
docker run -d --name ice-server \
  -p 8121:8121 \
  -v /your/data/path:/app/ice-data \
  waitmoon/ice-server:latest
```

或使用 docker-compose / Or use docker-compose:

```bash
docker-compose up -d
```

访问 / Visit: http://localhost:8121
