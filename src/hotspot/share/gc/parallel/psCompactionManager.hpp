/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#ifndef SHARE_GC_PARALLEL_PSCOMPACTIONMANAGER_HPP
#define SHARE_GC_PARALLEL_PSCOMPACTIONMANAGER_HPP

#include "classfile/classLoaderData.hpp"
#include "gc/parallel/psParallelCompact.hpp"
#include "gc/shared/partialArrayTaskStepper.hpp"
#include "gc/shared/preservedMarks.hpp"
#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/taskqueue.hpp"
#include "gc/shared/taskTerminator.hpp"
#include "memory/allocation.hpp"
#include "utilities/stack.hpp"

class MutableSpace;
class PSOldGen;
class ParCompactionManager;
class ObjectStartArray;
class ParallelCompactData;
class ParMarkBitMap;
class PartialArrayState;
class PartialArrayStateAllocator;

class PCMarkAndPushClosure: public ClaimMetadataVisitingOopIterateClosure {
  ParCompactionManager* _compaction_manager;

  template <typename T> void do_oop_work(T* p);
public:
  PCMarkAndPushClosure(ParCompactionManager* cm, ReferenceProcessor* rp) :
    ClaimMetadataVisitingOopIterateClosure(ClassLoaderData::_claim_stw_fullgc_mark, rp),
    _compaction_manager(cm) { }

  virtual void do_oop(oop* p)                     { do_oop_work(p); }
  virtual void do_oop(narrowOop* p)               { do_oop_work(p); }
};

class PSScannerTask {
private:
  void* _holder;

  static const uintptr_t PartialArrayStateBit = 1;

public:
  PSScannerTask() : _holder(nullptr) { }

  explicit PSScannerTask(oop obj) : _holder(obj) {
    assert(_holder != nullptr, "Not allowed to set null task queue element");
    assert(is_aligned(_holder, 1), "Misaligned");
  }

  explicit PSScannerTask(PartialArrayState* p) : _holder((void*)((uintptr_t)p | PartialArrayStateBit)) {
    assert(is_aligned(p, 1), "Misaligned");
  }

  oop obj() const {
    assert(is_oop(), "Trying to read partial array state " PTR_FORMAT " as oop", p2i(_holder));
    return cast_to_oop(_holder);
  }

  PartialArrayState* to_partial_array_state() const {
    assert(is_partial_array_state(), "Trying to read oop " PTR_FORMAT " as partial array state", p2i(_holder));
    return static_cast<PartialArrayState*>(static_cast<void*>(static_cast<char*>(_holder) - PartialArrayStateBit));
  }

  bool is_oop() const { return !is_partial_array_state(); }
  bool is_partial_array_state() const { return ((uintptr_t)_holder & PartialArrayStateBit) != 0; }
};

class ParCompactionManager : public CHeapObj<mtGC> {
  friend class MarkFromRootsTask;
  friend class ParallelCompactRefProcProxyTask;
  friend class ParallelScavengeRefProcProxyTask;
  friend class ParMarkBitMap;
  friend class PSParallelCompact;
  friend class FillDensePrefixAndCompactionTask;
  friend class PCAddThreadRootsMarkingTaskClosure;

 private:
  typedef OverflowTaskQueue<PSScannerTask, mtGC>         PSScannerTasksQueue;
  typedef GenericTaskQueueSet<PSScannerTasksQueue, mtGC> PSScannerTasksQueueSet;
  typedef OverflowTaskQueue<size_t, mtGC>                RegionTaskQueue;
  typedef GenericTaskQueueSet<RegionTaskQueue, mtGC>     RegionTaskQueueSet;

  static ParCompactionManager** _manager_array;
  static PSScannerTasksQueueSet* _marking_queues;
  static ObjectStartArray*      _start_array;
  static RegionTaskQueueSet*    _region_task_queues;
  static PSOldGen*              _old_gen;
  static PartialArrayStateAllocator*  _partial_array_state_allocator;

#if TASKQUEUE_STATS
  size_t                              _array_chunk_pushes;
  size_t                              _array_chunk_steals;
  size_t                              _arrays_chunked;
  size_t                              _array_chunks_processed;

  void print_local_stats(outputStream* const out, uint i) const;
  static void print_and_reset_taskqueue_stats();

  void reset_stats();
#endif // TASKQUEUE_STATS

  PSScannerTasksQueue           _marking_stack;
  PartialArrayTaskStepper       _partial_array_stepper;
  uint                          _partial_array_state_allocator_index;
  uint                          _min_array_size_for_chunking;

  size_t                        _next_shadow_region;

  PCMarkAndPushClosure _mark_and_push_closure;
  // Is there a way to reuse the _oop_stack for the
  // saving empty regions?  For now just create a different
  // type of TaskQueue.
  RegionTaskQueue              _region_stack;

  static PreservedMarksSet* _preserved_marks_set;
  PreservedMarks* _preserved_marks;

  static ParMarkBitMap* _mark_bitmap;

  // Contains currently free shadow regions. We use it in
  // a LIFO fashion for better data locality and utilization.
  static GrowableArray<size_t>* _shadow_region_array;

  // Provides mutual exclusive access of _shadow_region_array.
  // See pop/push_shadow_region_mt_safe() below
  static Monitor*               _shadow_region_monitor;

  StringDedup::Requests _string_dedup_requests;

  static PSOldGen* old_gen()             { return _old_gen; }
  static ObjectStartArray* start_array() { return _start_array; }
  static PSScannerTasksQueueSet* marking_queues()  { return _marking_queues; }

  static void initialize(ParMarkBitMap* mbm);

  void publish_and_drain_marking_tasks();

  ParCompactionManager(PreservedMarks* preserved_marks,
                       ReferenceProcessor* ref_processor);

  // Array of task queues.  Needed by the task terminator.
  static RegionTaskQueueSet* region_task_queues()      { return _region_task_queues; }
  PSScannerTasksQueue*  marking_stack()       { return &_marking_stack; }

  // To collect per-region live-words in a worker local cache in order to
  // reduce threads contention.
  class MarkingStatsCache : public CHeapObj<mtGC> {
    constexpr static size_t num_entries = 1024;
    static_assert(is_power_of_2(num_entries), "inv");
    static_assert(num_entries > 0, "inv");

    constexpr static size_t entry_mask = num_entries - 1;

    struct CacheEntry {
      size_t region_id;
      size_t live_words;
    };

    CacheEntry entries[num_entries] = {};

    inline void push(size_t region_id, size_t live_words);

  public:
    inline void push(oop obj, size_t live_words);

    inline void evict(size_t index);

    inline void evict_all();
  };

  MarkingStatsCache* _marking_stats_cache;

public:
  static const size_t InvalidShadow = ~0;
  static size_t  pop_shadow_region_mt_safe(PSParallelCompact::RegionData* region_ptr);
  static void    push_shadow_region_mt_safe(size_t shadow_region);
  static void    push_shadow_region(size_t shadow_region);
  static void    remove_all_shadow_regions();

  inline size_t  next_shadow_region() { return _next_shadow_region; }
  inline void    set_next_shadow_region(size_t record) { _next_shadow_region = record; }
  inline size_t  move_next_shadow_region_by(size_t workers) {
    _next_shadow_region += workers;
    return next_shadow_region();
  }

  void flush_string_dedup_requests() {
    _string_dedup_requests.flush();
  }

  static void flush_all_string_dedup_requests();

  RegionTaskQueue* region_stack()                { return &_region_stack; }

  // Get the compaction manager when doing evacuation work from the VM thread.
  // Simply use the first compaction manager here.
  static ParCompactionManager* get_vmthread_cm() { return _manager_array[0]; }

  PreservedMarks* preserved_marks() const {
    return _preserved_marks;
  }

  ParMarkBitMap* mark_bitmap() { return _mark_bitmap; }

  // Save for later processing.  Must not fail.
  inline void push(PSScannerTask task);
  inline void push_region(size_t index);

  // Check mark and maybe push on marking stack.
  template <typename T> inline void mark_and_push(T* p);

  // Access function for compaction managers
  static ParCompactionManager* gc_thread_compaction_manager(uint index);

  static bool steal(int queue_num, PSScannerTask& t);
  static bool steal(int queue_num, size_t& region);

  // Process tasks remaining on any marking stack
  void follow_marking_stacks();
  inline bool marking_stack_empty() const;

  // Process tasks remaining on any stack
  void drain_region_stacks();

  void follow_contents(PSScannerTask task);
  void push_objArray(oop obj);
  void follow_array(objArrayOop array, int start, int end);
  void process_array_chunk(PartialArrayState* state);

  TASKQUEUE_STATS_ONLY(inline void record_steal(PSScannerTask task);)

  class FollowStackClosure: public VoidClosure {
   private:
    ParCompactionManager* _compaction_manager;
    TaskTerminator* _terminator;
    uint _worker_id;
   public:
    FollowStackClosure(ParCompactionManager* cm, TaskTerminator* terminator, uint worker_id)
      : _compaction_manager(cm), _terminator(terminator), _worker_id(worker_id) { }
    virtual void do_void();
  };

  inline void create_marking_stats_cache();

  inline void flush_and_destroy_marking_stats_cache();

  // Called after marking.
  static void verify_all_marking_stack_empty() NOT_DEBUG_RETURN;

  // Region staks hold regions in from-space; called after compaction.
  static void verify_all_region_stack_empty() NOT_DEBUG_RETURN;
};

bool ParCompactionManager::marking_stack_empty() const {
  return _marking_stack.is_empty();
}

#endif // SHARE_GC_PARALLEL_PSCOMPACTIONMANAGER_HPP
