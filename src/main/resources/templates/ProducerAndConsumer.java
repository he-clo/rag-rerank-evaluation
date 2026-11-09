// 1. 定义消息类型（在队列中传递的）
interface Message {}

record ChunkMessage(List<Chunk> chunks) implements Message {}
record EmbeddingMessage(List<float[]> vectors, List<String> ids) implements Message {}

// 2. 阻塞队列（核心解耦点）
BlockingQueue<ChunkMessage> chunkQueue = new LinkedBlockingQueue<>(100); // 有界，背压
BlockingQueue<EmbeddingMessage> embedQueue = new LinkedBlockingQueue<>(50);

// 3. Splitter（生产者）
@Component
class Splitter {
    @Autowired BlockingQueue<ChunkMessage> chunkQueue;

    public void split(Document doc) {
        List<Chunk> chunks = doSplit(doc);
        // 按 batchSize 分批丢进队列
        for (List<Chunk> batch : partition(chunks, 32)) {
            chunkQueue.put(new ChunkMessage(batch));
        }
    }
}

// 4. EmbedWorker（消费者 + 生产者）
@Component
class EmbedWorker {
    @Autowired BlockingQueue<ChunkMessage> chunkQueue;
    @Autowired BlockingQueue<EmbeddingMessage> embedQueue;

    @PostConstruct
    public void start() {
        // 可以起多个线程并行消费
        Executors.newFixedThreadPool(4).submit(() -> {
            while (true) {
                ChunkMessage msg = chunkQueue.take(); // 阻塞等待
                List<float[]> vectors = ollamaEmbed(msg.chunks());
                embedQueue.put(new EmbeddingMessage(vectors, extractIds(msg.chunks())));
            }
        });
    }
}

// 5. Writer（批量写入）
@Component
class BatchWriter {
    @Autowired BlockingQueue<EmbeddingMessage> embedQueue;

    private List<EmbeddingMessage> buffer = new ArrayList<>();

    @Scheduled(fixedDelay = 1000) // 或者按 buffer 大小触发
    public void flush() {
        embedQueue.drainTo(buffer, 100); // 一次最多取100条
        if (!buffer.isEmpty()) {
            vectorDb.write(buffer);
            buffer.clear();
        }
    }
}
