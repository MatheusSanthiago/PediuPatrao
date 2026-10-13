# Conferência da Atividade Prática M1 Parte 2

Conferência do enunciado Atividade_Pratica_M1_Parte2.docx realizada em 12/10/2026. Esta lista identifica a implementação e as evidências, sem atribuir nota ou substituir a avaliação do professor.

## Requisitos funcionais e técnicos

| Item do Word | Implementação | Evidência |
| --- | --- | --- |
| RF01 — ADMIN, GERENTE e ATENDENTE | UsuarioDetailsService usa o perfil salvo; SecurityConfig e serviços restringem as operações | Login dos três perfis e bloqueio das APIs em FluxoCompletoTest |
| RF01 — gestão segura dos perfis | Somente ADMIN cria, altera e exclui usuários; a sessão acompanha mudanças de perfil | Testes de autoelevação por API e formulário e mudança de perfil em sessão aberta |
| RF01 — exclusividade do GERENTE | ADMIN e ATENDENTE não descontam, cancelam ou avançam status | Requisições com CSRF válido retornam 403 para esses perfis |
| RF02 — dados do pedido | Pedido e ItemPedido registram itens, quantidades, preços, descontos, total, responsável e entrada | Teste de criação via API e formulário com dois itens |
| RF02 — cálculo no servidor | Preço vem do produto cadastrado; totais e dados de controle informados pelo cliente são ignorados | Tentativa de enviar total 1 e status FINALIZADO resulta no total calculado e status RECEBIDO |
| RF03 — fluxo e bloqueios | PedidoService permite somente as seis transições válidas; registra saída nas duas modalidades | PedidoServiceTest verifica as 49 combinações entre os sete estados; testes de saída e finalização |
| RF04 — desconto | GERENTE, de 0 a 20%, antes da saída; substitui desconto anterior | Testes de limite, valores inválidos, substituição e bloqueios após saída/cancelamento |
| RF04 — cancelamento | Exige justificativa, preserva itens, impede novas alterações e registra auditoria | Teste de justificativa vazia e bloqueio de status, desconto e novo cancelamento |
| RF05 — persistência | Auditoria, AuditoriaRepository e AuditoriaService, em coleção MongoDB própria | Testes com MongoDB real isolado e contagem dos eventos |
| RF05 — identificação e mudanças | Usuário, perfil, instante, operação, entidade, ID, campos, valores anteriores/novos e justificativa | Comparação de telefone e endereço e eventos de pedidos |
| RF05 — consulta e integridade | Apenas ADMIN/GERENTE consultam; não existem rotas de edição/exclusão | POST, PUT e DELETE bloqueados; filtros de entidade e ID testados |
| RF05 — proteção de credenciais | JSON de usuários não inclui senha; auditoria usa lista explícita de campos de clientes/pedidos | Teste de ausência de senha na API e inspeção dos campos auditados |
| Regras nos serviços e autorização no backend | Services com validações e PreAuthorize; rotas protegidas pelo Spring Security | Testes diretos das APIs, independentemente da interface |
| CSRF | Ativo em login, formulários e APIs de alteração | Ausência de token retorna 403; formulários com token válido funcionam |
| Preservação dos cadastros | Clientes e produtos mantêm cadastro, consulta, edição e exclusão com permissões | Testes de clientes, produtos e formulários; clientes com pedidos não são excluídos |
| Consistência da auditoria | Transações abrangem alterações e auditoria; documentos usam controle de versão | Falha proposital na auditoria desfaz a alteração; cópia antiga não sobrescreve pedido |
| README | Execução, replica set, migração de usuários antigos, requisitos, alterações e testes | README.md |
| Testes | 30 testes executados, sem falhas ou erros, nenhum teste pulado | Maven package com mongodb.integration=true; BUILD SUCCESS |

## Opções originais do menu

O submenu Configurações foi restaurado com Funcionários, Entregadores, Horário de Funcionamento, Produtos e Usuários. As opções permanecem visíveis; restrições de perfil aparecem identificadas. O perfil da sessão aparece no menu.

No código original, Funcionários, Entregadores e Horário de Funcionamento eram somente links: não existiam controllers, serviços ou telas de cadastro. Seus atalhos agora abrem uma explicação de indisponibilidade em vez de uma rota inexistente. Esses cadastros não são exigidos pelo Word e não são contabilizados como funcionalidades implementadas.

Produtos, Usuários, Clientes e Pedidos continuam com suas operações implementadas, conforme as permissões do enunciado.

## Entrega e limites da conferência

- O código e o README estão preparados para o repositório individual. A publicação deve ser verificada no GitHub.
- O vídeo individual ainda precisa ser gravado diretamente da tela pelo aluno. O roteiro está no README. Nenhum vídeo foi criado nesta conferência; a sessão não dispõe de navegador ou aplicativo conectado para gravação.
- A inspeção visual em navegador permanece pendente. As páginas Thymeleaf e os formulários foram verificados pelos testes da aplicação.
- A regra de similaridade entre trabalhos depende de comparação com os outros projetos e da avaliação do professor; não é possível certificar esse percentual apenas com este repositório.
- O banco MongoDB precisa estar em replica set para as transações. Os testes usam um processo próprio e não alteram o banco habitual.
