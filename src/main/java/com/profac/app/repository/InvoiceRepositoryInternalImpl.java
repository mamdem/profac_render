package com.profac.app.repository;

import com.profac.app.domain.Invoice;
import com.profac.app.domain.Product;
import com.profac.app.repository.rowmapper.InvoiceRowMapper;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityOperations;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.SimpleR2dbcRepository;
import org.springframework.data.relational.core.sql.Comparison;
import org.springframework.data.relational.core.sql.Condition;
import org.springframework.data.relational.core.sql.Conditions;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.SelectBuilder.SelectFromAndJoin;
import org.springframework.data.relational.core.sql.Table;
import org.springframework.data.relational.repository.support.MappingRelationalEntityInformation;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC custom repository implementation for the Invoice entity.
 */
@SuppressWarnings("unused")
class InvoiceRepositoryInternalImpl extends SimpleR2dbcRepository<Invoice, Long> implements InvoiceRepositoryInternal {

    private final DatabaseClient db;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final EntityManager entityManager;

    private final InvoiceRowMapper invoiceMapper;

    private static final Table entityTable = Table.aliased("invoice", EntityManager.ENTITY_ALIAS);

    private static final EntityManager.LinkTable productsLink = new EntityManager.LinkTable(
        "rel_invoice__products",
        "invoice_id",
        "products_id"
    );

    public InvoiceRepositoryInternalImpl(
        R2dbcEntityTemplate template,
        EntityManager entityManager,
        InvoiceRowMapper invoiceMapper,
        R2dbcEntityOperations entityOperations,
        R2dbcConverter converter
    ) {
        super(
            new MappingRelationalEntityInformation(converter.getMappingContext().getRequiredPersistentEntity(Invoice.class)),
            entityOperations,
            converter
        );
        this.db = template.getDatabaseClient();
        this.r2dbcEntityTemplate = template;
        this.entityManager = entityManager;
        this.invoiceMapper = invoiceMapper;
    }

    @Override
    public Flux<Invoice> findAllBy(Pageable pageable) {
        return createQuery(pageable, null).all();
    }

    RowsFetchSpec<Invoice> createQuery(Pageable pageable, Condition whereClause) {
        List<Expression> columns = InvoiceSqlHelper.getColumns(entityTable, EntityManager.ENTITY_ALIAS);
        SelectFromAndJoin selectFrom = Select.builder().select(columns).from(entityTable);
        // we do not support Criteria here for now as of https://github.com/jhipster/generator-jhipster/issues/18269
        String select = entityManager.createSelect(selectFrom, Invoice.class, pageable, whereClause);
        return db.sql(select).map(this::process);
    }

    @Override
    public Flux<Invoice> findAll() {
        return findAllBy(null);
    }

    @Override
    public Mono<Invoice> findById(Long id) {
        Comparison whereClause = Conditions.isEqual(entityTable.column("id"), Conditions.just(id.toString()));
        return createQuery(null, whereClause).one();
    }

    @Override
    public Mono<Invoice> findOneWithEagerRelationships(Long id) {
        return findById(id);
    }

    @Override
    public Flux<Invoice> findAllWithEagerRelationships() {
        return findAll();
    }

    @Override
    public Flux<Invoice> findAllWithEagerRelationships(Pageable page) {
        return findAllBy(page);
    }

    private Invoice process(Row row, RowMetadata metadata) {
        Invoice entity = invoiceMapper.apply(row, "e");
        return entity;
    }

    @Override
    public <S extends Invoice> Mono<S> save(S entity) {
        return super.save(entity).flatMap((S e) -> updateRelations(e));
    }

    protected <S extends Invoice> Mono<S> updateRelations(S entity) {
        Mono<Void> result = entityManager
            .updateLinkTable(productsLink, entity.getId(), entity.getProducts().stream().map(Product::getId))
            .then();
        return result.thenReturn(entity);
    }

    @Override
    public Mono<Void> deleteById(Long entityId) {
        return deleteRelations(entityId).then(super.deleteById(entityId));
    }

    protected Mono<Void> deleteRelations(Long entityId) {
        return entityManager.deleteFromLinkTable(productsLink, entityId);
    }
}
