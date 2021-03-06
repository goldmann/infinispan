package org.infinispan.query.blackbox;

import static org.infinispan.query.helper.TestQueryHelperFactory.createQueryParser;

import java.util.List;
import java.util.NoSuchElementException;

import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.query.CacheQuery;
import org.infinispan.query.FetchOptions;
import org.infinispan.query.ResultIterator;
import org.infinispan.query.Search;
import org.infinispan.query.test.Person;
import org.infinispan.test.MultipleCacheManagersTest;
import org.testng.annotations.Test;

/**
 * ClusteredQueryTest.
 * 
 * @author Israel Lacerra <israeldl@gmail.com>
 * @since 5.1
 */
@Test(groups = "functional", testName = "query.blackbox.ClusteredQueryTest")
public class ClusteredQueryTest extends MultipleCacheManagersTest {

   Cache<String, Person> cacheAMachine1, cacheAMachine2;
   Person person1;
   Person person2;
   Person person3;
   Person person4;
   QueryParser queryParser;
   Query luceneQuery;
   CacheQuery cacheQuery;
   final String key1 = "Navin";
   final String key2 = "BigGoat";
   final String key3 = "MiniGoat";

   public ClusteredQueryTest() {
      // BasicConfigurator.configure();
      cleanup = CleanupPhase.AFTER_METHOD;
   }

   protected void enhanceConfig(ConfigurationBuilder cacheCfg) {
      // meant to be overridden
   }

   @Override
   protected void createCacheManagers() throws Throwable {
      ConfigurationBuilder cacheCfg = getDefaultClusteredCacheConfig(getCacheMode(), false);
      cacheCfg
         .indexing()
            .enable()
            .indexLocalOnly(true)
            .addProperty("default.directory_provider", "ram")
            .addProperty("lucene_version", "LUCENE_CURRENT");
      enhanceConfig(cacheCfg);
      List<Cache<String, Person>> caches = createClusteredCaches(2, cacheCfg);
      cacheAMachine1 = caches.get(0);
      cacheAMachine2 = caches.get(1);
   }

   protected CacheMode getCacheMode() {
      return CacheMode.REPL_SYNC;
   }

   protected void prepareTestData() {
      person1 = new Person();
      person1.setName("NavinSurtani");
      person1.setBlurb("Likes playing WoW");
      person1.setAge(45);

      person2 = new Person();
      person2.setName("BigGoat");
      person2.setBlurb("Eats grass");
      person2.setAge(30);

      person3 = new Person();
      person3.setName("MiniGoat");
      person3.setBlurb("Eats cheese");
      person3.setAge(35);

      // Put the 3 created objects in the cache1.

      cacheAMachine2.put(key1, person1);
      cacheAMachine1.put(key2, person2);
      cacheAMachine1.put(key3, person3);

      person4 = new Person();
      person4.setName("MightyGoat");
      person4.setBlurb("Also eats grass");
      person4.setAge(66);

      cacheAMachine1.put("newOne", person4);
   }

   public void testLazyOrdered() throws ParseException {
      populateCache();

      // applying sort
      SortField sortField = new SortField("age", SortField.INT);
      Sort sort = new Sort(sortField);
      cacheQuery.sort(sort);

      for (int i = 0; i < 2; i ++) {
         ResultIterator iterator = cacheQuery.iterator(new FetchOptions().fetchMode(FetchOptions.FetchMode.LAZY));
         try {
            assert cacheQuery.getResultSize() == 4 : cacheQuery.getResultSize();

            int previousAge = 0;
            while (iterator.hasNext()) {
               Person person = (Person) iterator.next();
               assert person.getAge() > previousAge;
               previousAge = person.getAge();
            }
         }
         finally {
            iterator.close();
         }
      }
   }

   public void testLazyNonOrdered() throws ParseException {
      populateCache();

      ResultIterator iterator = cacheQuery.iterator(new FetchOptions().fetchMode(FetchOptions.FetchMode.LAZY));
      try {
         assert cacheQuery.getResultSize() == 4 : cacheQuery.getResultSize();
      }
      finally {
         iterator.close();
      }
   }

   public void testEagerOrdered() throws ParseException {
      populateCache();

      // applying sort
      SortField sortField = new SortField("age", SortField.INT);
      Sort sort = new Sort(sortField);
      cacheQuery.sort(sort);

      ResultIterator iterator = cacheQuery.iterator(new FetchOptions().fetchMode(FetchOptions.FetchMode.EAGER));
      try {
         assert cacheQuery.getResultSize() == 4 : cacheQuery.getResultSize();

         int previousAge = 0;
         while (iterator.hasNext()) {
            Person person = (Person) iterator.next();
            assert person.getAge() > previousAge;
            previousAge = person.getAge();
         }
      } finally {
         iterator.close();
      }
   }

   @Test(expectedExceptions = NoSuchElementException.class, expectedExceptionsMessageRegExp = "Out of boundaries")
   public void testIteratorNextOutOfBounds() throws Exception {
      populateCache();

      cacheQuery.maxResults(1);
      ResultIterator iterator = cacheQuery.iterator(new FetchOptions().fetchMode(FetchOptions.FetchMode.EAGER));
      try {
         assert iterator.hasNext();
         iterator.next();

         assert !iterator.hasNext();
         iterator.next();
      } finally {
         iterator.close();
      }
   }

   @Test(expectedExceptions = UnsupportedOperationException.class)
   public void testIteratorRemove() throws Exception {
      populateCache();

      cacheQuery.maxResults(1);
      ResultIterator iterator = cacheQuery.iterator(new FetchOptions().fetchMode(FetchOptions.FetchMode.EAGER));
      try {
         assert iterator.hasNext();
         iterator.remove();
      } finally {
         iterator.close();
      }
   }

   public void testList() throws ParseException {
      populateCache();

      // applying sort
      SortField sortField = new SortField("age", SortField.INT);
      Sort sort = new Sort(sortField);
      cacheQuery.sort(sort);

      List<Object> results = cacheQuery.list();
      assert results.size() == 4 : cacheQuery.getResultSize();

      int previousAge = 0;
      for (Object result : results) {
         Person person = (Person) result;
         assert person.getAge() > previousAge;
         previousAge = person.getAge();
      }
   }
   
   public void testGetResultSizeList() throws ParseException {
      populateCache();
      assert cacheQuery.getResultSize() == 4 : cacheQuery.getResultSize();
   }

   public void testPagination() throws ParseException {
      populateCache();

      cacheQuery.firstResult(2);
      cacheQuery.maxResults(1);

      // applying sort
      SortField sortField = new SortField("age", SortField.INT);
      Sort sort = new Sort(sortField);
      cacheQuery.sort(sort);

      List<Object> results = cacheQuery.list();
      assert results.size() == 1;
      assert cacheQuery.getResultSize() == 4;
      assert ((Person) (results.get(0))).getAge() == 45;
   }

   private void populateCache() throws ParseException {
      prepareTestData();
      Query[] queries = new Query[2];
      queryParser = createQueryParser("blurb");

      luceneQuery = queryParser.parse("eats");
      queries[0] = luceneQuery;

      luceneQuery = queryParser.parse("playing");
      queries[1] = luceneQuery;

      luceneQuery = luceneQuery.combine(queries);
      cacheQuery = Search.getSearchManager(cacheAMachine1).getClusteredQuery(luceneQuery);
   }

}
