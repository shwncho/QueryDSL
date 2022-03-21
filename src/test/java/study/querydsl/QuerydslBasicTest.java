package study.querydsl;


import com.fasterxml.jackson.databind.deser.std.StdKeyDeserializer;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;
    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before(){
        queryFactory = new JPAQueryFactory(em);
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1",10,teamA);
        Member member2 = new Member("member2",20,teamA);
        Member member3 = new Member("member3",30,teamB);
        Member member4 = new Member("member4",40,teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);

        //초기화
        em.flush();
        em.clear();

        List<Member> members = em.createQuery("select m from Member m", Member.class)
                .getResultList();

        for (Member member : members) {
            System.out.println("member = " + member);
            System.out.println("->member.team = " + member.getTeam());

        }
    }

    @Test
    public void startJPQL(){

        //member1을 찾기
        Member findMember = em.createQuery("select m from Member m where m.username = :username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl(){

        QMember m = QMember.member;

        Member findMember = queryFactory
                .select(m)
                .from(m)
                .where(m.username.eq("member1")) // 파라미터 바인딩 처리
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");

    }

    @Test
    public void search(){

        QMember m = QMember.member;
        Member findMember = queryFactory
                .selectFrom(m)
                .where(m.username.eq("member1").
                        and(m.age.eq(10)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam(){

        QMember m = QMember.member;
        Member findMember = queryFactory
                .selectFrom(m)
                .where(
                        m.username.eq("member1"),
                        m.age.eq(10)
                )
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void resultFetch(){
        QMember m = QMember.member;
//        List<Member> fetch = queryFactory
//                .selectFrom(m)
//                .fetch();
//
//        Member fetchOne = queryFactory
//                .selectFrom(m)
//                .fetchOne();
//
//        Member fetchFirst = queryFactory
//                .selectFrom(m)
//                .fetchFirst();

        QueryResults<Member> results = queryFactory
                .selectFrom(m)
                .fetchResults();
        results.getTotal();
        List<Member> content = results.getResults();


    }

    @Test
    public void count(){
        QMember m = QMember.member;
        Long totalCount = queryFactory
                .select(m.count())
                .from(m)
                .fetchOne();

        System.out.println("totalCount = " + totalCount);
    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순
     * 2. 회원 이름 올림차순
     * 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
     */
    @Test
    public void sort(){
        QMember m = QMember.member;
        em.persist(new Member(null,100));
        em.persist(new Member("member5",100));
        em.persist(new Member("member6",100));

        List<Member> result = queryFactory
                .selectFrom(m)
                .where(m.age.eq(100))
                .orderBy(m.age.desc(), m.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();

    }

    @Test
    public void paging1(){
        QMember m = QMember.member;
        List<Member> result = queryFactory
                .selectFrom(m)
                .orderBy(m.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    public void paging2(){
        QMember m = QMember.member;
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(m)
                .orderBy(m.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);
        assertThat(queryResults.getResults().size()).isEqualTo(2);
    }

    @Test
    public void aggregation(){
        QMember m = QMember.member;
        List<Tuple> result = queryFactory
                .select(
                        m.count(),
                        m.age.sum(),
                        m.age.avg(),
                        m.age.max(),
                        m.age.min()
                )
                .from(m)
                .fetch();
        Tuple tuple = result.get(0);
        assertThat(tuple.get(m.count())).isEqualTo(4);
        assertThat(tuple.get(m.age.sum())).isEqualTo(100);
        assertThat(tuple.get(m.age.avg())).isEqualTo(25);
        assertThat(tuple.get(m.age.max())).isEqualTo(40);
        assertThat(tuple.get(m.age.min())).isEqualTo(10);

    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구해라
     */
    @Test
    public void group() throws Exception {
        QMember m = QMember.member;
        List<Tuple> result = queryFactory
                .select(team.name, m.age.avg())
                .from(m)
                .join(m.team, team)
                .groupBy(team.name)
                .fetch();
        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(m.age.avg())).isEqualTo(15);
        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(m.age.avg())).isEqualTo(35);

    }

}
