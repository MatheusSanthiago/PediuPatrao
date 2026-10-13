# PediuPatrão

Sistema de pedidos de pizzaria desenvolvido com Java 21, Spring Boot 3.4.4, Spring Security, Thymeleaf e MongoDB. Esta versão implementa os requisitos da Atividade Prática M1 Parte 2.

## Executar

Requisitos: JDK 21 ou superior, MongoDB em **replica set** e Maven (ou o wrapper incluído). A versão Java do projeto continua sendo 21.

As alterações de pedidos/clientes e a auditoria são gravadas na mesma transação. Por isso, um MongoDB standalone aceita consultas, mas **não atende às operações de escrita desta versão**. Um replica set local com um único membro é suficiente para estudar e demonstrar a atividade.

### Banco local com Docker

Na pasta do projeto:

```powershell
docker compose up -d --wait
$env:MONGODB_URI = 'mongodb://localhost:27018/pizzaria?replicaSet=rs0&directConnection=true'
$env:MONGODB_DATABASE = 'pizzaria'
$env:APP_ADMIN_PASSWORD = 'EscolhaUmaSenhaForte'
.\mvnw.cmd spring-boot:run
```

O Compose usa a porta 27018 e um volume próprio. Não altera o MongoDB existente na porta 27017. Aguarde o serviço ficar saudável antes de iniciar o Java.

Abra http://localhost:8080. Em um banco vazio, o login inicial é `admin`, com a senha definida em `APP_ADMIN_PASSWORD`. O nome inicial pode ser alterado por `APP_ADMIN_USERNAME`. Não há senha fixa no código. Essas variáveis só criam o primeiro administrador quando a coleção de usuários está vazia.

### Banco local sem Docker

Com `mongod` e `mongosh` instalados, crie uma pasta de dados separada e inicie:

```powershell
New-Item -ItemType Directory -Force .local/mongo
mongod --dbpath .local/mongo --port 27018 --replSet rs0 --bind_ip 127.0.0.1
```

Em outro terminal, inicialize o replica set uma única vez:

```powershell
mongosh --port 27018 --eval 'rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27018"}]})'
```

Depois use as mesmas variáveis e o comando Maven da seção anterior. O caminho dos executáveis pode ser necessário se não estiverem no PATH. A pasta `.local` fica fora do Git.

### Banco já existente

A configuração padrão continua apontando para `localhost:27017/pizzaria`. Para aproveitar dados existentes, configure esse MongoDB como replica set ou use uma conexão de replica set em `MONGODB_URI`, mantendo `MONGODB_DATABASE` com o nome correto. Faça backup antes de qualquer migração manual do banco.

Usuários antigos com perfil `USER` ou sem perfil entram como ATENDENTE. O programa não promove contas antigas automaticamente. Se ainda não existir um ADMIN, o responsável pelo banco precisa atribuir esse perfil à conta escolhida. Exemplo no mongosh, conectado ao banco correto:

```javascript
use pizzaria
db.usuarios.updateOne({username: "admin"}, {$set: {role: "ADMIN"}})
```

O inicializador adiciona o campo técnico `versao` aos pedidos e clientes antigos, sem inventar preços, horários ou responsáveis históricos. Pedidos antigos permanecem visíveis; estados antigos fora do fluxo novo não são convertidos automaticamente. Crie pedidos novos para demonstrar a atividade. Logins duplicados precisam ser corrigidos antes da criação do índice único.

## Conferência do enunciado e opções do menu

A [conferência por requisito](docs/CONFERENCIA_ATIVIDADE.md) relaciona as exigências do Word, os arquivos responsáveis, os testes e as etapas de entrega ainda pendentes.

O submenu **Configurações** contém novamente **Funcionários, Entregadores, Horário de Funcionamento, Produtos e Usuários**. As opções restritas aparecem identificadas pelo perfil, sem liberar operações proibidas no backend. O menu mostra o perfil da sessão para explicar por que uma operação está restrita.

Funcionários, Entregadores e Horário de Funcionamento eram atalhos sem implementação no projeto original. Agora abrem uma página informando sua indisponibilidade. O Word não exige esses três cadastros; a restauração dos atalhos não equivale à implementação desses módulos.

O filtro da auditoria aceita somente a entidade (todos os pedidos ou todos os clientes), ou entidade e identificador. A opção Todas reúne os registros dos dois tipos.

## Perfis e permissões

| Operação | ADMIN | GERENTE | ATENDENTE |
| --- | --- | --- | --- |
| Administrar usuários e atribuir perfis | Sim | Não | Não |
| Consultar pedidos | Sim | Sim | Sim |
| Registrar pedidos | Não | Sim | Sim |
| Avançar status e registrar saída | Não | Sim | Não |
| Aplicar desconto e cancelar | Não | Sim | Não |
| Consultar clientes | Não | Sim | Sim |
| Cadastrar, editar e excluir clientes sem pedidos | Não | Não | Sim |
| Consultar auditoria | Sim | Sim | Não |
| Consultar produtos | Sim | Sim | Sim |
| Cadastrar, editar e excluir produtos | Sim | Sim | Não |

O enunciado não define a gestão de produtos nem o perfil responsável pela exclusão de clientes. Nesta implementação, ADMIN e GERENTE mantêm produtos; ATENDENTE mantém clientes. Clientes com pedidos não podem ser excluídos. O GERENTE gerencia o andamento dos pedidos; o ATENDENTE acompanha pela consulta.

Não existe herança entre ADMIN e GERENTE. O ADMIN **não pode** cancelar pedidos ou aplicar descontos.

As rotas e os serviços validam o perfil. Alterações de perfil e exclusões passam a valer na próxima requisição, inclusive nas sessões já abertas. O cadastro público de usuários foi removido. Senhas não aparecem nas respostas JSON nem no formulário de edição. A própria conta administrativa não pode ser excluída nem perder seu perfil pela interface.

## Requisitos implementados

- **RF01:** três perfis, autenticação com o perfil salvo, proteção de páginas/APIs e gestão de usuários exclusiva do ADMIN.
- **RF02:** itens com produto, quantidade, nome e preço no momento da compra; subtotal, desconto e total calculados no servidor. Valores, status, IDs e responsáveis enviados pelo cliente não controlam esses dados na criação.
- **RF03:** fluxo RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA ou RETIRADO → FINALIZADO, com horários e responsáveis pela entrada e saída.
- **RF04:** desconto percentual entre 0 e 20, com até duas casas decimais, e cancelamento com justificativa de até 500 caracteres. Apenas GERENTE, antes da saída. Pedido cancelado não pode mudar novamente.
- **RF05:** coleção própria de auditoria com usuário, perfil, data/hora, operação, entidade, identificador e valores anteriores/novos. Inclusões e alterações de pedidos, descontos, cancelamentos e alterações/inclusões/exclusões de clientes são registradas.
- Auditoria somente para consulta; não existem operações públicas de alteração ou exclusão. A página e a API paginam 30 registros e permitem filtrar entidade e identificador.
- Formulários com proteção CSRF e logout por POST. APIs autenticadas por sessão também exigem token para alterações.
- Tratamento de dados inválidos, acesso negado, conflito de versão e falha de banco.
- Telas de cadastro de pedidos, andamento, descontos, cancelamento e auditoria; fluxo de produtos corrigido; CSS e scripts dos formulários separados do HTML.

Descontos substituem o percentual anterior e sempre usam o subtotal original, sem acumular descontos sucessivos. Zero remove o desconto. Os cálculos usam BigDecimal e arredondamento para centavos. Os preços dos itens anteriores não mudam quando o cadastro do produto muda.

Datas são armazenadas em UTC e exibidas no horário de São Paulo. A auditoria guarda apenas os campos necessários dos pedidos/clientes; não inclui senhas, tokens ou credenciais. Endereços e telefones completos são consultáveis apenas pelos perfis autorizados para a finalidade da auditoria. Não há exportação pública.

O controle de versão e as transações evitam que gravações simultâneas sobrescrevam mudanças sem aviso. Se a gravação da auditoria falhar, a alteração do pedido/cliente é desfeita. Não é necessário adicionar JPA, SQL, filas ou serviços externos.

## Organização

- `entity`: dados de Usuario, Cliente, Produto, Pedido, ItemPedido, Auditoria e Alteracao.
- `repository`: acesso ao MongoDB.
- `service`: regras de pedidos, clientes, usuários, produtos e auditoria.
- `controller`: páginas, APIs e tratamento de erros.
- `config`: segurança, atualização do perfil da sessão, primeiro administrador e transações.
- `templates` e `static`: Thymeleaf, JavaScript e CSS.

Os nomes e as camadas existentes foram preservados. Os principais arquivos alterados são SecurityConfig, UsuarioDetailsService, UsuarioService, Pedido, PedidoService, ClienteService e HomeController.

## Testes

Testes de regras de negócio, sem banco:

```powershell
.\mvnw.cmd test
```

Testes completos com MongoDB real e isolado:

```powershell
$env:MONGOD_PATH = 'C:\Program Files\MongoDB\Server\9.0\bin\mongod.exe'
.\mvnw.cmd -Dmongodb.integration=true test
```

Adapte o caminho à instalação local. Os testes iniciam um processo próprio, em porta livre e diretório temporário dentro de `target`, criam o banco `pizzaria_teste` e encerram esse processo ao terminar. Não conectam ao banco configurado em application.properties. Sem a propriedade `mongodb.integration=true`, os testes de integração são pulados.

Em JDK 25, se o Mockito informar versão de Java não suportada, acrescente `"-Dnet.bytebuddy.experimental=true"` ou execute com JDK 21.

A suíte cobre combinações de status, dinheiro informado indevidamente, limite e substituição de descontos, saída, cancelamento, login dos três perfis, páginas Thymeleaf, CSRF, permissões das APIs, alteração de perfil em sessão aberta, dados anteriores/novos de clientes e rollback quando a auditoria falha.

## Demonstração individual

Use somente clientes fictícios na demonstração:

1. Entre como ADMIN. Cadastre um GERENTE e um ATENDENTE e cadastre produtos ativos.
2. Entre como ATENDENTE. Cadastre um cliente e registre um pedido com mais de um item.
3. Mostre que o ATENDENTE não acessa auditoria nem administração de usuários.
4. Entre como GERENTE. Aplique 20% de desconto e mostre a rejeição de 20,01%.
5. Avance por preparação, pronto, saída e finalizado. Confira horários e responsáveis.
6. Crie outro pedido e cancele com justificativa. Demonstre que ele não pode mudar novamente.
7. Como ATENDENTE, altere telefone e endereço do cliente.
8. Como GERENTE ou ADMIN, consulte a auditoria dos pedidos e do cliente e confira valores anteriores/novos.
9. Demonstre também bloqueios diretos nas APIs, não apenas botões ocultos.

No console do navegador, em uma sessão local autenticada, obtenha o CSRF e tente uma operação restrita. Use o identificador de um pedido de demonstração existente:

```javascript
const csrf = await fetch('/api/csrf').then(r => r.json());
const resposta = await fetch('/api/pedidos/ID_DO_PEDIDO/desconto?percentual=10', {
  method: 'PUT',
  headers: { [csrf.headerName]: csrf.token }
});
console.log(resposta.status);
```

Com ADMIN ou ATENDENTE, o resultado deve ser **403**, mesmo com CSRF válido. Com GERENTE, em pedido antes da saída, deve ser **200**. Sem autenticação, consultas à API retornam **401**. Não compartilhe tokens ou cookies.

Entrega: revisar as alterações, publicar no seu repositório GitHub individual e gravar o vídeo da sua própria execução. O vídeo e a publicação no GitHub não são produzidos automaticamente pelo aplicativo.

## Referências técnicas

- [Spring Security e proteção CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Data MongoDB e transações](https://docs.spring.io/spring-data/mongodb/reference/mongodb/client-session-transactions.html)
