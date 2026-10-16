import com.mongodb.client.*;
import org.bson.Document;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class PrepararReplica {
    static final String URI = "mongodb://127.0.0.1:27017/?directConnection=true&serverSelectionTimeoutMS=10000";
    static final List<String> COLECOES = List.of("usuarios", "clientes", "produtos", "pedidos", "auditoria", "funcionarios", "entregadores", "horariosAtendimento");
    public static void main(String[] args) throws Exception {
        try (var cliente = MongoClients.create(URI)) {
            var admin = cliente.getDatabase("admin");
            if (args[0].equals("iniciar")) {
                Document hello = admin.runCommand(new Document("hello", 1));
                if (hello.getString("setName") == null) {
                    admin.runCommand(new Document("replSetInitiate",
                        new Document("_id", "rs0").append("members",
                            List.of(new Document("_id", 0).append("host", "localhost:27017")))));
                } else if (!"rs0".equals(hello.getString("setName"))) {
                    throw new IllegalStateException("Replica set diferente de rs0.");
                }
                long limite = System.nanoTime() + Duration.ofSeconds(45).toNanos();
                while (!Boolean.TRUE.equals(admin.runCommand(new Document("hello", 1)).getBoolean("isWritablePrimary"))) {
                    if (System.nanoTime() > limite) throw new IllegalStateException("O banco não se tornou primário.");
                    Thread.sleep(200);
                }
                System.out.println("Replica set rs0 PRIMARY em localhost:27017.");
            } else {
                var banco = cliente.getDatabase("pizzaria");
                Document resumo = new Document();
                for (String nome : COLECOES) {
                    List<String> documentos = new ArrayList<>();
                    for (Document doc : banco.getCollection(nome).find()) documentos.add(doc.toJson());
                    Collections.sort(documentos);
                    byte[] hash = MessageDigest.getInstance("SHA-256").digest(String.join("\n", documentos).getBytes(StandardCharsets.UTF_8));
                    resumo.append(nome, new Document("quantidade", documentos.size()).append("sha256", HexFormat.of().formatHex(hash)));
                }
                Path arquivo = Path.of(args[1]);
                if (args[0].equals("conferir")) {
                    Document anterior = Document.parse(Files.readString(arquivo));
                    if (!anterior.equals(resumo)) throw new IllegalStateException("O conteúdo mudou desde a cópia de referência.");
                    System.out.println("Todos os registros e credenciais preservados: comparação SHA-256 aprovada.");
                } else {
                    Files.writeString(arquivo, resumo.toJson());
                    for (String nome : COLECOES)
                        System.out.println(nome + ": " + resumo.get(nome, Document.class).getInteger("quantidade"));
                }
            }
        }
    }
}