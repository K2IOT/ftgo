# Paging State Implementation for Order History Service

## Overview

The Order History Service now supports **stateless pagination** using Cassandra/ScyllaDB's native paging state mechanism. This allows clients to efficiently paginate through large result sets without maintaining server-side session state.

## How It Works

### 1. First Page Request

```http
GET /api/consumers/123/orders?pageSize=20
```

**Response:**
```json
{
  "orders": [...],
  "totalCount": 20,
  "pageSize": 20,
  "hasNext": true,
  "pagingState": "AQAAABQAAAABAAAABgAAAA..." // Base64-encoded opaque token
}
```

### 2. Subsequent Page Requests

```http
GET /api/consumers/123/orders?pageSize=20&pagingState=AQAAABQAAAABAAAABgAAAA...
```

**Response:**
```json
{
  "orders": [...],
  "totalCount": 20,
  "pageSize": 20,
  "hasNext": true,
  "pagingState": "AQAAABQAAAABAAAABgAAAA..." // New token for next page
}
```

### 3. Last Page

```http
GET /api/consumers/123/orders?pageSize=20&pagingState=...
```

**Response:**
```json
{
  "orders": [...],
  "totalCount": 15,
  "pageSize": 20,
  "hasNext": false,
  "pagingState": null // No more pages
}
```

## Implementation Details

### CassandraPageRequest API

The implementation uses Spring Data Cassandra's `CassandraPageRequest` class:

```java
// First page
CassandraPageRequest pageable = CassandraPageRequest.first(pageSize);

// Subsequent pages with paging state
byte[] pagingStateBytes = Base64.getDecoder().decode(pagingState);
ByteBuffer pagingStateBuffer = ByteBuffer.wrap(pagingStateBytes);
CassandraPageRequest pageable = CassandraPageRequest.of(
    CassandraPageRequest.first(pageSize), 
    pagingStateBuffer
);
```

### Paging State Encoding

The paging state is:
1. **Opaque**: Clients should treat it as an opaque token
2. **Base64-encoded**: For safe transmission in URLs
3. **Stateless**: No server-side session required
4. **Position-specific**: Represents the exact position in the result set

### Error Handling

If an invalid paging state is provided:
- **HTTP 400 Bad Request** is returned
- Client should restart pagination from the first page

## Benefits

### 1. Scalability
- No server-side session state
- Horizontal scaling without session affinity
- Minimal memory footprint

### 2. Performance
- Efficient Cassandra/ScyllaDB native pagination
- No need to skip records
- Constant-time page retrieval

### 3. Consistency
- Paging state captures the exact query position
- Works correctly with Cassandra's eventual consistency model

## Limitations

### 1. Paging State Validity
- Paging states may become invalid if:
  - The underlying data is compacted
  - The cluster topology changes
  - Too much time passes (implementation-dependent)

### 2. No Random Access
- Cannot jump to arbitrary pages (e.g., page 5)
- Must traverse pages sequentially
- No total count available (Cassandra limitation)

### 3. Filter Changes
- Changing filters requires restarting pagination
- Paging state is specific to the original query

## Best Practices

### For Clients

1. **Store paging state**: Keep the paging state from each response
2. **Handle errors**: Be prepared to restart pagination on 400 errors
3. **Don't parse**: Treat paging state as opaque
4. **Sequential access**: Traverse pages in order

### For the Service

1. **Validate input**: Check paging state format before decoding
2. **Return 400**: For invalid paging states
3. **Document behavior**: Make limitations clear to clients
4. **Monitor**: Track pagination errors

## Example Client Code

### JavaScript/TypeScript

```typescript
async function fetchAllOrders(consumerId: number): Promise<Order[]> {
  const allOrders: Order[] = [];
  let pagingState: string | null = null;
  
  do {
    const params = new URLSearchParams({
      pageSize: '50',
      ...(pagingState && { pagingState })
    });
    
    const response = await fetch(
      `/api/consumers/${consumerId}/orders?${params}`
    );
    
    if (!response.ok) {
      if (response.status === 400 && pagingState) {
        // Invalid paging state, restart from beginning
        pagingState = null;
        continue;
      }
      throw new Error(`HTTP ${response.status}`);
    }
    
    const data = await response.json();
    allOrders.push(...data.orders);
    pagingState = data.hasNext ? data.pagingState : null;
    
  } while (pagingState);
  
  return allOrders;
}
```

### Java

```java
public List<OrderHistoryRecord> fetchAllOrders(Long consumerId) {
    List<OrderHistoryRecord> allOrders = new ArrayList<>();
    String pagingState = null;
    
    do {
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromPath("/api/consumers/{consumerId}/orders")
            .queryParam("pageSize", 50);
        
        if (pagingState != null) {
            builder.queryParam("pagingState", pagingState);
        }
        
        ResponseEntity<OrderHistoryResponse> response = restTemplate.getForEntity(
            builder.buildAndExpand(consumerId).toUri(),
            OrderHistoryResponse.class
        );
        
        if (response.getStatusCode() == HttpStatus.BAD_REQUEST && pagingState != null) {
            // Invalid paging state, restart
            pagingState = null;
            continue;
        }
        
        OrderHistoryResponse data = response.getBody();
        allOrders.addAll(data.getOrders());
        pagingState = data.isHasNext() ? data.getPagingState() : null;
        
    } while (pagingState != null);
    
    return allOrders;
}
```

## Testing

### Manual Testing with curl

```bash
# First page
curl "http://localhost:8080/api/consumers/123/orders?pageSize=5"

# Next page (use pagingState from response)
curl "http://localhost:8080/api/consumers/123/orders?pageSize=5&pagingState=AQAAABQAAAABAAAABgAAAA..."

# With filters
curl "http://localhost:8080/api/consumers/123/orders?pageSize=5&status=APPROVED&since=2026-01-01"
```

### Integration Testing

The paging state functionality should be tested with:
1. **Small page sizes**: Verify multiple pages work correctly
2. **Invalid states**: Verify 400 error handling
3. **Filter combinations**: Verify paging works with filters
4. **Last page**: Verify hasNext=false and pagingState=null

## References

- [Spring Data Cassandra Documentation](https://docs.spring.io/spring-data/cassandra/docs/current/reference/html/)
- [Cassandra Paging](https://docs.datastax.com/en/developer/java-driver/4.15/manual/core/paging/)
- [ScyllaDB Paging](https://docs.scylladb.com/stable/cql/paging.html)

## Requirements Validated

- **Requirement 9.7**: Pagination with page size and continuation token
- **Requirement 9.4**: Query by consumer_id returns orders sorted by creation_date DESC
- **Requirement 9.5**: Filtering by status, date range, restaurant, keyword
