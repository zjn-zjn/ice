FROM golang:1.21-alpine AS builder

WORKDIR /build
COPY go.mod ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o ice-server .

FROM alpine:3.19

RUN apk add --no-cache tzdata

WORKDIR /app
COPY --from=builder /build/ice-server .

EXPOSE 8121

VOLUME /app/ice-data

ENTRYPOINT ["./ice-server"]
CMD ["-storage-path", "/app/ice-data"]
