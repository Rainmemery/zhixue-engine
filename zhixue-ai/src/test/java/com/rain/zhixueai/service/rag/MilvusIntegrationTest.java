package com.rain.zhixueai.service.rag;

import com.rain.zhixueai.config.MilvusClientProvider;
import com.rain.zhixueai.config.MilvusProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@DisplayName("Milvus 集成测试")
public class MilvusIntegrationTest {

    @Autowired
    private MilvusClientProvider milvusClientProvider;

    @Autowired
    private MilvusProperties milvusProperties;

    private MilvusClientV2 milvusClient;
    private static final String TEST_COLLECTION_PREFIX = "test_collection";
    private static final int TEST_DIMENSION = 1024;
    private String testCollectionName;

    @BeforeEach
    @DisplayName("测试前准备")
    public void setUp() {
        log.info("Setting up Milvus integration test");
        
        assertNotNull(milvusClientProvider, "MilvusClientProvider should not be null");
        assertNotNull(milvusProperties, "MilvusProperties should not be null");
        
        if (milvusClientProvider.isAvailable()) {
            milvusClient = milvusClientProvider.getClient();
            assertNotNull(milvusClient, "Milvus client should be available");
            
            testCollectionName = TEST_COLLECTION_PREFIX + "_" + TEST_DIMENSION + "_" + 
                    System.currentTimeMillis();
            log.info("Test collection name: {}", testCollectionName);
        } else {
            log.warn("Milvus is not available, skipping test setup");
        }
    }

    @AfterEach
    @DisplayName("测试后清理")
    public void tearDown() {
        if (milvusClient != null && testCollectionName != null) {
            try {
                log.info("Cleaning up test collection: {}", testCollectionName);
                
                DropCollectionReq dropReq = DropCollectionReq.builder()
                        .collectionName(testCollectionName)
                        .build();
                milvusClient.dropCollection(dropReq);
                
                log.info("Test collection dropped successfully");
            } catch (Exception e) {
                log.warn("Failed to drop test collection: {}", e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Task 1: Milvus 连接测试")
    public void testMilvusConnection() {
        log.info("=== Starting Milvus Connection Test ===");
        
        if (!milvusClientProvider.isAvailable()) {
            log.warn("Milvus is not available, skipping connection test");
            return;
        }
        
        assertNotNull(milvusClient, "Milvus client should be initialized");
        assertTrue(milvusClientProvider.isAvailable(), "Milvus client should be available");
        
        String protocol = milvusProperties.getSecure() ? "https" : "http";
        String expectedUri = protocol + "://" + milvusProperties.getHost() + ":" + milvusProperties.getPort();
        log.info("Expected Milvus URI: {}", expectedUri);
        
        assertTrue(milvusClientProvider.isHealthy(), "Milvus client should be healthy");
        
        log.info("=== Milvus Connection Test Passed ===");
    }

    @Test
    @DisplayName("Task 2: HNSW 索引创建测试")
    public void testHNSWIndexCreation() {
        log.info("=== Starting HNSW Index Creation Test ===");
        
        if (!milvusClientProvider.isAvailable()) {
            log.warn("Milvus is not available, skipping index creation test");
            return;
        }
        
        try {
            log.info("Creating test collection: {}", testCollectionName);
            
            CreateCollectionReq.FieldSchema idField = CreateCollectionReq.FieldSchema.builder()
                    .name("id")
                    .dataType(DataType.VarChar)
                    .maxLength(64)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build();
            
            CreateCollectionReq.FieldSchema vectorField = CreateCollectionReq.FieldSchema.builder()
                    .name("vector")
                    .dataType(DataType.FloatVector)
                    .dimension(TEST_DIMENSION)
                    .build();
            
            CreateCollectionReq.FieldSchema contentField = CreateCollectionReq.FieldSchema.builder()
                    .name("content")
                    .dataType(DataType.VarChar)
                    .maxLength(8192)
                    .build();
            
            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                    .fieldSchemaList(Arrays.asList(idField, vectorField, contentField))
                    .build();
            
            CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                    .collectionName(testCollectionName)
                    .collectionSchema(schema)
                    .build();
            
            milvusClient.createCollection(createCollectionReq);
            log.info("Collection created successfully");
            
            Thread.sleep(1000);
            
            DescribeCollectionReq describeReq = DescribeCollectionReq.builder()
                    .collectionName(testCollectionName)
                    .build();
            DescribeCollectionResp describeResp = milvusClient.describeCollection(describeReq);
            
            assertNotNull(describeResp, "Describe collection response should not be null");
            assertEquals(testCollectionName, describeResp.getCollectionName(), 
                    "Collection name should match");
            log.info("Collection schema verified");
            
            log.info("Creating HNSW index with M=16, efConstruction=200");
            
            io.milvus.v2.common.IndexParam indexParam = io.milvus.v2.common.IndexParam.builder()
                    .fieldName("vector")
                    .indexType(io.milvus.v2.common.IndexParam.IndexType.HNSW)
                    .metricType(io.milvus.v2.common.IndexParam.MetricType.COSINE)
                    .build();
            
            CreateIndexReq createIndexReq = CreateIndexReq.builder()
                    .collectionName(testCollectionName)
                    .indexParams(Collections.singletonList(indexParam))
                    .build();
            
            milvusClient.createIndex(createIndexReq);
            log.info("HNSW index creation request sent");
            
            Thread.sleep(3000);
            
            DescribeIndexReq describeIndexReq = DescribeIndexReq.builder()
                    .collectionName(testCollectionName)
                    .fieldName("vector")
                    .build();
            
            DescribeIndexResp indexResp = milvusClient.describeIndex(describeIndexReq);
            
            assertNotNull(indexResp, "Describe index response should not be null");
            assertNotNull(indexResp.getIndexDescriptions(), "Index descriptions should not be null");
            assertFalse(indexResp.getIndexDescriptions().isEmpty(), 
                    "Index descriptions should not be empty");
            
            boolean hnswIndexFound = false;
            for (var indexDesc : indexResp.getIndexDescriptions()) {
                if ("vector".equals(indexDesc.getFieldName())) {
                    log.info("Found index: type={}, metricType={}", 
                            indexDesc.getIndexType(), indexDesc.getMetricType());
                    assertEquals(io.milvus.v2.common.IndexParam.IndexType.HNSW, 
                            indexDesc.getIndexType(), "Index type should be HNSW");
                    assertEquals(io.milvus.v2.common.IndexParam.MetricType.COSINE, 
                            indexDesc.getMetricType(), "Metric type should be COSINE");
                    hnswIndexFound = true;
                    break;
                }
            }
            
            assertTrue(hnswIndexFound, "HNSW index should be found");
            log.info("=== HNSW Index Creation Test Passed ===");
            
        } catch (Exception e) {
            log.error("HNSW index creation test failed: {}", e.getMessage(), e);
            fail("HNSW index creation failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Task 3: 向量相似度计算测试")
    public void testVectorSimilarityCalculation() {
        log.info("=== Starting Vector Similarity Calculation Test ===");
        
        if (!milvusClientProvider.isAvailable()) {
            log.warn("Milvus is not available, skipping similarity test");
            return;
        }
        
        try {
            createTestCollection();
            
            float[] vector1 = generateRandomVector(TEST_DIMENSION);
            float[] vector2 = generateRandomVector(TEST_DIMENSION);
            float[] vector3 = vector1.clone();
            
            for (int i = 0; i < vector3.length; i++) {
                vector3[i] = vector1[i] * 0.9f;
            }
            
            insertTestVectors(vector1, vector2, vector3);
            
            loadCollection();
            
            log.info("Searching with vector1 (should find vector3 as most similar)");
            SearchReq searchReq = SearchReq.builder()
                    .collectionName(testCollectionName)
                    .data(Collections.singletonList(new FloatVec(vector1)))
                    .topK(3)
                    .outputFields(Collections.singletonList("content"))
                    .build();
            
            SearchResp searchResp = milvusClient.search(searchReq);
            
            assertNotNull(searchResp, "Search response should not be null");
            List<List<SearchResp.SearchResult>> results = searchResp.getSearchResults();
            
            assertNotNull(results, "Search results should not be null");
            assertFalse(results.isEmpty(), "Search results should not be empty");
            assertEquals(3, results.get(0).size(), "Should return 3 results");
            
            List<SearchResp.SearchResult> searchResults = results.get(0);
            
            SearchResp.SearchResult topResult = searchResults.get(0);
            assertEquals("1", topResult.getId().toString(), 
                    "Top result should be vector1 itself (id=1)");
            
            float topScore = topResult.getScore();
            log.info("Top result score (self-similarity): {}", topScore);
            assertTrue(topScore < 0.1f, "Self-similarity distance should be very small");
            
            for (SearchResp.SearchResult result : searchResults) {
                float score = result.getScore();
                float similarity = 1.0f - score;
                
                log.info("Result id={}, score={}, similarity={}", 
                        result.getId(), score, similarity);
                
                assertTrue(similarity >= 0.0f && similarity <= 1.0f, 
                        "Similarity should be in range [0, 1]");
            }
            
            log.info("=== Vector Similarity Calculation Test Passed ===");
            
        } catch (Exception e) {
            log.error("Vector similarity test failed: {}", e.getMessage(), e);
            fail("Vector similarity test failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Task 4: 向量插入与查询测试")
    public void testVectorInsertAndQuery() {
        log.info("=== Starting Vector Insert and Query Test ===");
        
        if (!milvusClientProvider.isAvailable()) {
            log.warn("Milvus is not available, skipping insert/query test");
            return;
        }
        
        try {
            createTestCollection();
            
            int numVectors = 10;
            float[][] vectors = new float[numVectors][];
            for (int i = 0; i < numVectors; i++) {
                vectors[i] = generateRandomVector(TEST_DIMENSION);
            }
            
            log.info("Inserting {} test vectors", numVectors);
            insertTestVectors(vectors);
            
            loadCollection();
            
            float[] queryVector = vectors[0].clone();
            log.info("Querying with first vector");
            
            SearchReq searchReq = SearchReq.builder()
                    .collectionName(testCollectionName)
                    .data(Collections.singletonList(new FloatVec(queryVector)))
                    .topK(5)
                    .outputFields(Collections.singletonList("content"))
                    .build();
            
            SearchResp searchResp = milvusClient.search(searchReq);
            
            assertNotNull(searchResp, "Search response should not be null");
            List<List<SearchResp.SearchResult>> results = searchResp.getSearchResults();
            
            assertNotNull(results, "Search results should not be null");
            assertFalse(results.isEmpty(), "Search results should not be empty");
            
            List<SearchResp.SearchResult> searchResults = results.get(0);
            log.info("Search returned {} results", searchResults.size());
            assertEquals(5, searchResults.size(), "Should return 5 results");
            
            SearchResp.SearchResult topResult = searchResults.get(0);
            assertEquals("1", topResult.getId().toString(), 
                    "Top result should be the query vector itself");
            
            float[] knowledgeBaseIds = {1L, 2L, 3L};
            log.info("Testing filter with knowledge_base_id in [1, 2, 3]");
            
            String filter = "knowledge_base_id in [1, 2, 3]";
            SearchReq filteredSearchReq = SearchReq.builder()
                    .collectionName(testCollectionName)
                    .data(Collections.singletonList(new FloatVec(queryVector)))
                    .filter(filter)
                    .topK(5)
                    .outputFields(Collections.singletonList("content"))
                    .build();
            
            SearchResp filteredSearchResp = milvusClient.search(filteredSearchReq);
            assertNotNull(filteredSearchResp, "Filtered search response should not be null");
            
            log.info("=== Vector Insert and Query Test Passed ===");
            
        } catch (Exception e) {
            log.error("Vector insert and query test failed: {}", e.getMessage(), e);
            fail("Vector insert and query test failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Task 5: 批量向量插入测试")
    public void testBatchVectorInsert() {
        log.info("=== Starting Batch Vector Insert Test ===");
        
        if (!milvusClientProvider.isAvailable()) {
            log.warn("Milvus is not available, skipping batch insert test");
            return;
        }
        
        try {
            createTestCollection();
            
            int batchSize = 50;
            List<JsonObject> data = new java.util.ArrayList<>();
            
            log.info("Preparing {} vectors for batch insert", batchSize);
            for (int i = 0; i < batchSize; i++) {
                JsonObject row = new JsonObject();
                String id = UUID.randomUUID().toString();
                row.addProperty("id", id);
                row.addProperty("content", "Test content " + i);
                
                float[] vector = generateRandomVector(TEST_DIMENSION);
                JsonArray embeddingArray = new JsonArray();
                for (float v : vector) {
                    embeddingArray.add(v);
                }
                row.add("vector", embeddingArray);
                
                data.add(row);
            }
            
            log.info("Inserting batch of {} vectors", batchSize);
            long startTime = System.currentTimeMillis();
            
            InsertReq insertReq = InsertReq.builder()
                    .collectionName(testCollectionName)
                    .data(data)
                    .build();
            
            milvusClient.insert(insertReq);
            
            long insertDuration = System.currentTimeMillis() - startTime;
            log.info("Batch insert completed in {}ms ({} vectors/sec)", 
                    insertDuration, (batchSize * 1000.0) / insertDuration);
            
            loadCollection();
            
            float[] queryVector = generateRandomVector(TEST_DIMENSION);
            SearchReq searchReq = SearchReq.builder()
                    .collectionName(testCollectionName)
                    .data(Collections.singletonList(new FloatVec(queryVector)))
                    .topK(10)
                    .build();
            
            SearchResp searchResp = milvusClient.search(searchReq);
            
            assertNotNull(searchResp, "Search response should not be null");
            List<List<SearchResp.SearchResult>> results = searchResp.getSearchResults();
            
            assertNotNull(results, "Search results should not be null");
            assertFalse(results.isEmpty(), "Search results should not be empty");
            
            int resultCount = results.get(0).size();
            log.info("Search returned {} results after batch insert", resultCount);
            assertTrue(resultCount > 0, "Should return at least one result");
            
            log.info("=== Batch Vector Insert Test Passed ===");
            
        } catch (Exception e) {
            log.error("Batch vector insert test failed: {}", e.getMessage(), e);
            fail("Batch vector insert test failed: " + e.getMessage());
        }
    }

    private void createTestCollection() throws Exception {
        log.info("Creating test collection: {}", testCollectionName);
        
        CreateCollectionReq.FieldSchema idField = CreateCollectionReq.FieldSchema.builder()
                .name("id")
                .dataType(DataType.VarChar)
                .maxLength(64)
                .isPrimaryKey(true)
                .autoID(false)
                .build();
        
        CreateCollectionReq.FieldSchema knowledgeBaseIdField = CreateCollectionReq.FieldSchema.builder()
                .name("knowledge_base_id")
                .dataType(DataType.Int64)
                .build();
        
        CreateCollectionReq.FieldSchema vectorField = CreateCollectionReq.FieldSchema.builder()
                .name("vector")
                .dataType(DataType.FloatVector)
                .dimension(TEST_DIMENSION)
                .build();
        
        CreateCollectionReq.FieldSchema contentField = CreateCollectionReq.FieldSchema.builder()
                .name("content")
                .dataType(DataType.VarChar)
                .maxLength(8192)
                .build();
        
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .fieldSchemaList(Arrays.asList(idField, knowledgeBaseIdField, vectorField, contentField))
                .build();
        
        CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                .collectionName(testCollectionName)
                .collectionSchema(schema)
                .build();
        
        milvusClient.createCollection(createCollectionReq);
        log.info("Test collection created successfully");
        
        Thread.sleep(1000);
        
        io.milvus.v2.common.IndexParam indexParam = io.milvus.v2.common.IndexParam.builder()
                .fieldName("vector")
                .indexType(io.milvus.v2.common.IndexParam.IndexType.HNSW)
                .metricType(io.milvus.v2.common.IndexParam.MetricType.COSINE)
                .build();
        
        CreateIndexReq createIndexReq = CreateIndexReq.builder()
                .collectionName(testCollectionName)
                .indexParams(Collections.singletonList(indexParam))
                .build();
        
        milvusClient.createIndex(createIndexReq);
        log.info("HNSW index created successfully");
        
        Thread.sleep(2000);
    }

    private void insertTestVectors(float[]... vectors) throws Exception {
        List<JsonObject> data = new java.util.ArrayList<>();
        
        for (int i = 0; i < vectors.length; i++) {
            JsonObject row = new JsonObject();
            row.addProperty("id", String.valueOf(i + 1));
            row.addProperty("knowledge_base_id", 1L);
            row.addProperty("content", "Test content " + (i + 1));
            
            JsonArray embeddingArray = new JsonArray();
            for (float v : vectors[i]) {
                embeddingArray.add(v);
            }
            row.add("vector", embeddingArray);
            
            data.add(row);
        }
        
        InsertReq insertReq = InsertReq.builder()
                .collectionName(testCollectionName)
                .data(data)
                .build();
        
        milvusClient.insert(insertReq);
        log.info("Inserted {} test vectors", vectors.length);
        
        Thread.sleep(1000);
    }

    private void loadCollection() throws Exception {
        log.info("Loading collection into memory");
        
        io.milvus.v2.service.collection.request.LoadCollectionReq loadReq = 
                io.milvus.v2.service.collection.request.LoadCollectionReq.builder()
                        .collectionName(testCollectionName)
                        .build();
        
        milvusClient.loadCollection(loadReq);
        log.info("Collection loaded successfully");
        
        Thread.sleep(1000);
    }

    private float[] generateRandomVector(int dimension) {
        float[] vector = new float[dimension];
        float norm = 0.0f;
        
        for (int i = 0; i < dimension; i++) {
            vector[i] = (float) (Math.random() * 2 - 1);
            norm += vector[i] * vector[i];
        }
        
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < dimension; i++) {
            vector[i] /= norm;
        }
        
        return vector;
    }
}
