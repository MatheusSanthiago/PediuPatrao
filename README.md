# PediuPatrão

O PediuPatrão é um sistema para uma pizzaria. Ele permite cadastrar clientes e produtos, registrar pedidos e acompanhar o atendimento até a entrega ou retirada.

O projeto foi desenvolvido com Java, Spring Boot, Spring Security, Thymeleaf e MongoDB.

## O que o sistema faz

- Cadastro, consulta, edição e exclusão de clientes e produtos, conforme a permissão do usuário.
- Cadastro de usuários com os perfis ADMIN, GERENTE e ATENDENTE.
- Cadastro e edição de funcionários e entregadores, com situação ativo ou inativo.
- Escolha do entregador e acompanhamento das entregas.
- Horários de funcionamento para os sete dias da semana.
- Registro de pedidos com produtos, quantidades e preços.
- Cálculo automático do valor do pedido.
- Acompanhamento do preparo e da saída dos pedidos.
- Aplicação de descontos e cancelamento de pedidos.
- Consulta do histórico de alterações de clientes e pedidos.

Clientes que já possuem pedidos não podem ser excluídos.

## Perfis de acesso

| Perfil | O que pode fazer |
| --- | --- |
| ADMIN | Gerenciar usuários e produtos, consultar pedidos e visualizar o histórico de alterações |
| GERENTE | Registrar e acompanhar pedidos, mudar o andamento, aplicar descontos, cancelar pedidos, gerenciar produtos, consultar clientes e visualizar o histórico |
| ATENDENTE | Cadastrar e atualizar clientes, excluir clientes sem pedidos, consultar produtos, registrar pedidos e acompanhar seu andamento |

Somente o GERENTE pode aplicar descontos ou cancelar pedidos. Essa regra também vale para o ADMIN.

## Andamento dos pedidos

O pedido passa pelas seguintes etapas:

1. Recebido.
2. Em preparação.
3. Pronto.
4. Saiu para entrega ou retirado.
5. Finalizado.

O sistema registra o horário de entrada, o horário de saída e os responsáveis. Não é permitido pular etapas.

O desconto pode ser de até 20% e só pode ser aplicado antes da saída. O cancelamento também precisa acontecer antes da saída e exige uma justificativa. Depois de cancelado, o pedido não pode mais ser alterado.

## Histórico de alterações

A opção Auditoria mostra quem fez uma alteração, quando ela aconteceu e quais dados foram modificados. Por exemplo, ao trocar o telefone de um cliente, ficam registrados o número anterior e o novo.

Esse histórico também registra a criação dos pedidos, as mudanças de status, os descontos e os cancelamentos. Somente ADMIN e GERENTE podem consultá-lo.

## Como executar

É necessário ter Java 21 ou superior e o MongoDB configurado.

O MongoDB precisa funcionar como um **replica set**, uma configuração que permite salvar a alteração e seu histórico juntos. Sem isso, as consultas funcionam, mas algumas operações de cadastro e edição não são concluídas.

### Usando um banco já configurado

Na pasta do projeto, abra o PowerShell e execute:

```powershell
.\mvnw.cmd spring-boot:run
```

Por padrão, o sistema usa o banco `pizzaria`, na porta `27017`, com replica set `rs0`. Nesta instalação local, essa configuração já foi feita e os dados existentes foram preservados.

Se o banco estiver vazio, defina a senha do primeiro administrador antes de iniciar:

```powershell
$env:APP_ADMIN_PASSWORD = 'EscolhaUmaSenhaForte'
.\mvnw.cmd spring-boot:run
```

O usuário criado será `admin`. Em um banco que já possui usuários, use as contas existentes.

### Criando um banco com Docker

O projeto inclui o arquivo `compose.yaml`, que prepara um banco com a configuração necessária. Com o Docker instalado e em execução, use:

```powershell
docker compose up -d --wait
$env:MONGODB_URI = 'mongodb://localhost:27018/pizzaria?replicaSet=rs0&directConnection=true'
$env:MONGODB_DATABASE = 'pizzaria'
$env:APP_ADMIN_PASSWORD = 'EscolhaUmaSenhaForte'
.\mvnw.cmd spring-boot:run
```

Essa opção usa um banco separado, na porta `27018`. Os dados do banco que já existe na porta `27017` não são copiados automaticamente.

Depois de iniciar, abra **http://localhost:8080**.

Contas antigas com perfil USER ou sem perfil entram como ATENDENTE. Para administrar usuários, é preciso usar uma conta com perfil ADMIN.

## Organização do projeto

- `entity`: classes que representam clientes, produtos, pedidos e usuários.
- `repository`: classes que acessam o banco de dados.
- `service`: regras do sistema, como cálculo de valores e mudança de status.
- `controller`: recebe as ações feitas nas páginas e nas APIs.
- `config`: configurações de acesso e do banco.
- `templates`: páginas HTML.
- `static`: arquivos CSS, JavaScript e imagens.

## Principais alterações

Foram adicionados os três perfis de acesso, o controle das etapas dos pedidos, os descontos, o cancelamento com justificativa e o histórico de alterações. As permissões são verificadas pelo sistema, mesmo quando alguém tenta acessar uma operação diretamente pela API.

O menu Configurações permite cadastrar funcionários, entregadores, produtos e horários de funcionamento. ADMIN e GERENTE podem alterar funcionários, entregadores e horários. ATENDENTE pode consultar esses dados.

## Equipe, entregas e horários

- **Funcionários:** informe nome, telefone e cargo. Ao editar, é possível marcar o funcionário como inativo. Esse cadastro não cria uma conta de acesso.
- **Entregadores:** informe nome, telefone e veículo. Apenas entregadores ativos podem receber novas entregas.
- **Entregas:** quando o pedido estiver Pronto, o GERENTE escolhe o entregador e clica em Sair para entrega. Depois, pode finalizar a entrega. O nome do entregador fica registrado no pedido e a atribuição aparece no histórico.
- **Horário de Funcionamento:** escolha um dia, indique se está aberto ou fechado e salve. Por exemplo, das 18:00 às 02:00 significa que fecha na madrugada do dia seguinte. Os horários são informativos e não impedem o cadastro de pedidos.

Os dias ainda não configurados aparecem como fechados. Os cadastros de funcionários, entregadores e horários também funcionam com MongoDB sem replica set; alterações em pedidos e entregas precisam da configuração descrita em Como executar.

## Dashboard administrativo

ADMIN e GERENTE podem abrir **Dashboard** no menu ou acessar `/admin`. O painel mostra total vendido, pedidos finalizados, ticket médio, itens vendidos, gráficos diários, situação dos pedidos e os cinco produtos mais vendidos.

Escolha a data inicial e final e clique em **Atualizar painel**. O período usa a data de entrada dos pedidos, no horário de Brasília, e aceita até 366 dias. Os indicadores de venda consideram apenas pedidos finalizados com dados completos e valores após os descontos. A situação dos pedidos inclui também os demais status.

Os dados são consultados em `/api/dashboard/resumo`, `/api/dashboard/vendas`, `/api/dashboard/status` e `/api/dashboard/produtos`. Pedidos antigos sem data ou sem informações suficientes são identificados por avisos. O painel não altera os pedidos.

A [documentação da atividade em Word](docs/DASHBOARD.docx) contém os requisitos, exemplos da API, regras dos cálculos e resultados dos testes. As [evidências dos testes](docs/evidencias/testes-dashboard.txt) também estão no repositório.

## Vídeo de apresentação

O [vídeo da atividade](docs/ATIVIDADE.mp4) tem 3 minutos e mostra o dashboard funcionando, os filtros, os perfis de acesso e as principais decisões do projeto.

## Testes

Para executar os testes das regras dos pedidos:

```powershell
.\mvnw.cmd test
```

Também existem testes que verificam o sistema com um banco separado. Para executá-los, informe o caminho do MongoDB instalado:

```powershell
$env:MONGOD_PATH = 'C:\Program Files\MongoDB\Server\9.0\bin\mongod.exe'
.\mvnw.cmd "-Dmongodb.integration=true" test
```

Ajuste o caminho de acordo com a sua instalação. Esses testes não alteram os dados do banco usado normalmente pelo projeto.

Os testes do dashboard são executados junto com os anteriores. Em instalações com Java 25, a versão atual do Mockito precisa da opção `"-Dnet.bytebuddy.experimental=true"` no comando Maven. Com Java 21 essa opção não é necessária.

Para testar também o JavaScript, com Node.js instalado:

```powershell
node --test src/test/js/dashboard.test.cjs
```

Esses testes usam respostas de demonstração salvas em `docs/evidencias`, sem acessar o banco habitual.
