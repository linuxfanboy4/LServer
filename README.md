# LServer - High-Performance Java HTTP Server

## Overview

LServer is a robust HTTP server implementation in Java designed for efficiency and security. It delivers web content with optimal performance while maintaining strict access controls and resource management.

## Key Features

- **Concurrent Request Handling**: Processes multiple client connections simultaneously using dedicated threads
- **Security Mechanisms**:
  - Automatic rate limiting (100 requests/minute threshold)
  - Temporary IP blacklisting for abusive clients
  - Strict path confinement to document root
- **Performance Enhancements**:
  - On-the-fly gzip compression for text content
  - Configurable HTTP caching directives
  - Direct file system access for minimal overhead
- **Protocol Support**:
  - Full HTTP/1.1 compliance
  - Accurate Content-Type header generation
  - Automatic directory index generation

## Technical Specifications

- **Core Method**: GET
- **Content Type Support**:
  - Web Formats: HTML, CSS, JavaScript, JSON
  - Text: Plain Text
  - Images: PNG, JPEG, GIF, SVG, ICO
- **Compression**: Automatic gzip for HTML, CSS, and JavaScript
- **Cache Policy**: 1-hour browser caching by default

## Installation Procedure

1. Verify Java JDK installation (version 8+ required)
2. Acquire source code:
   ```bash
   wget https://raw.githubusercontent.com/linuxfanboy4/LServer/refs/heads/main/src/LServer.java
   ```
3. Compile executable:
   ```bash
   javac LServer.java
   ```

## Operational Parameters

### Standard Execution

```bash
java LServer
```
- Listens on TCP port 8000
- Serves files from current working directory

### Custom Configuration

```
--port [number]  Designate listening port (default: 8000)
--dir [path]     Specify document root (default: .)
```

Implementation Example:
```bash
java LServer --port 8080 --dir /var/www/html
```

## Configuration Parameters

Key tunable parameters (modify in source):

- `RATE_LIMIT`: Requests per minute threshold (default: 100)
- `BLOCK_TIME`: IP blacklist duration in milliseconds (default: 300000)
- Cache control directives in `cacheHeaders` mapping

## Security Architecture

1. **Request Throttling**: Automatic IP-based rate control
2. **Path Sanitization**: Absolute path verification prevents directory traversal
3. **Resource Isolation**: Strict filesystem access constraints
4. **Connection Integrity**: Guaranteed socket cleanup

## Performance Characteristics

Optimized operation through:

- Selective gzip compression
- Efficient byte-level file operations
- Lean resource utilization
- Immediate connection termination

Production deployment considerations:

1. Thread pool implementation
2. Persistent connection support
3. Advanced caching strategies

## Diagnostic Procedures

**Common Operational Issues:**

1. **Port Conflicts**: Verify port availability
2. **Access Permissions**: Confirm read rights for served content
3. **Network Policies**: Validate firewall/security group rules

**Log Format:**
Standard request logging pattern:
`[timestamp] client_ip method path status`

## Licensing

Open-source implementation. Use and modification permitted.

## Contribution Guidelines

Technical contributions accepted. Adhere to standard Java conventions and include relevant test cases.

## Performance Assessment

Recommended testing tools:

```bash
ab -n 1000 -c 100 http://localhost:8000/
```

Note: Adjust rate limiting parameters when conducting load tests.
