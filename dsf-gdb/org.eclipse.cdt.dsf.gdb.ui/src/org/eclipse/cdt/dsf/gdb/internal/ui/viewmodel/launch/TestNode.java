package org.eclipse.cdt.dsf.gdb.internal.ui.viewmodel.launch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.cdt.dsf.concurrent.CountingRequestMonitor;
import org.eclipse.cdt.dsf.concurrent.DataRequestMonitor;
import org.eclipse.cdt.dsf.concurrent.DsfExecutor;
import org.eclipse.cdt.dsf.concurrent.DsfRunnable;
import org.eclipse.cdt.dsf.concurrent.IDsfStatusConstants;
import org.eclipse.cdt.dsf.concurrent.Immutable;
import org.eclipse.cdt.dsf.concurrent.RequestMonitor;
import org.eclipse.cdt.dsf.datamodel.IDMContext;
import org.eclipse.cdt.dsf.debug.service.IBreakpointsExtension.IBreakpointHitDMEvent;
import org.eclipse.cdt.dsf.debug.service.IProcesses;
import org.eclipse.cdt.dsf.debug.service.IRunControl.IContainerSuspendedDMEvent;
import org.eclipse.cdt.dsf.debug.service.IRunControl.IExecutionDMContext;
import org.eclipse.cdt.dsf.debug.service.IRunControl.ISuspendedDMEvent;
import org.eclipse.cdt.dsf.gdb.service.IGDBProcesses.IThreadRemovedDMEvent;
import org.eclipse.cdt.dsf.debug.service.IStack.IFrameDMContext;
import org.eclipse.cdt.dsf.debug.service.IStack.IFrameDMData;
import org.eclipse.cdt.dsf.debug.service.IStack;
import org.eclipse.cdt.dsf.debug.service.command.ICommandControlService;
import org.eclipse.cdt.dsf.debug.service.command.ICommandControlService.ICommandControlShutdownDMEvent;
import org.eclipse.cdt.dsf.internal.ui.DsfUIPlugin;
import org.eclipse.cdt.dsf.mi.service.IMIExecutionDMContext;
import org.eclipse.cdt.dsf.service.DsfServicesTracker;
import org.eclipse.cdt.dsf.service.DsfSession;
import org.eclipse.cdt.dsf.ui.concurrent.ViewerDataRequestMonitor;
import org.eclipse.cdt.dsf.ui.viewmodel.AbstractVMContext;
import org.eclipse.cdt.dsf.ui.viewmodel.IVMContext;
import org.eclipse.cdt.dsf.ui.viewmodel.IVMNode;
import org.eclipse.cdt.dsf.ui.viewmodel.IVMProvider;
import org.eclipse.cdt.dsf.ui.viewmodel.VMDelta;
import org.eclipse.cdt.dsf.ui.viewmodel.datamodel.AbstractDMVMProvider;
import org.eclipse.cdt.dsf.ui.viewmodel.datamodel.IDMVMContext;
import org.eclipse.cdt.dsf.ui.viewmodel.properties.IElementPropertiesProvider;
import org.eclipse.cdt.dsf.ui.viewmodel.properties.IPropertiesUpdate;
import org.eclipse.cdt.dsf.ui.viewmodel.properties.LabelAttribute;
import org.eclipse.cdt.dsf.ui.viewmodel.properties.LabelColumnInfo;
import org.eclipse.cdt.dsf.ui.viewmodel.properties.LabelText;
import org.eclipse.cdt.dsf.ui.viewmodel.properties.PropertiesBasedLabelProvider;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IChildrenCountUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IChildrenUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IElementLabelProvider;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IHasChildrenUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.ILabelUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IModelDelta;

public class TestNode implements IVMNode, IElementLabelProvider, IElementPropertiesProvider {
	
	public final String TEST_PROPERTY = "test.node.property"; //$NON-NLS-1$
	public final String TEST_ID = "test.node.if"; //$NON-NLS-1$
	public final String STACKNODE_THREAD_LIST = "stacknode.comparator.thread.list"; //$NON-NLS-1$
	private StackNodeDM fDeltaElement = null;
	
	public class StackNodeDM extends PlatformObject {
		
		private String fId; 
		private DsfSession fSession;
		private AbstractDMVMProvider fProvider;	
		private StackNodeDM fParent;		
		private List<IDMContext> fThreads;		
		private HashMap<String, StackNodeDM> fMap;
		
		public StackNodeDM(String id, AbstractDMVMProvider provider, DsfSession session) {
			fId = id;
			fProvider = provider;
			fSession = session;
			fThreads = new ArrayList<IDMContext>();
			fMap = new HashMap<>();
		}		
		
		/*
		@Override
		public boolean equals(Object obj) {
			return false;
		}
		*/
		
		/* Getters */
		public StackNodeDM getItem(String key) { return fMap.get(key); }
		public DsfSession getSession() { return fSession; }
		public AbstractDMVMProvider getProvider() { return fProvider; }
		public String getId() { return fId; }
		public HashMap<String, StackNodeDM> getMap() { return fMap; }		
		public Collection<StackNodeDM> getChildren() { return fMap.values(); }	
		public IDMContext getDMContext() { return fThreads.size() > 0 ? fThreads.get(0) : null; }
		public List<IDMContext> getDMContexts() { return fThreads; }
		@SuppressWarnings("unchecked")
		@Override
	    public <T> T getAdapter(Class<T> adapterType) {
	        T retVal = (T)fSession.getModelAdapter(adapterType);
	        if (retVal == null) {
	            retVal = super.getAdapter(adapterType);
	        }
	        return retVal;
	    }
		
		public StackNodeDM add(String key) {
			StackNodeDM child = new StackNodeDM(key, fProvider, fSession);
			fMap.put(key, child);
			return child;
		}
		public void putAll(StackNodeDM node) {
			fMap.putAll(node.fMap);
		}		
		public void addThread(IDMContext thread) {
			fThreads.add(thread);
		}		
		public StackNodeDM addThreads(StackNodeDM node) {
			for(IDMContext context : node.fThreads)
				this.fThreads.add(context);
			return this;
		}		
		public void addThreads(List<IDMContext> contexts) {
			for(IDMContext context : contexts) {
				fThreads.add(context);
			}
		}
		@Override
		public String toString() {
			return "StackNodeDM " + fId;
		}
		/**
		 * Return true if it is a leaf (i.e. it does not have children)
		 * @return boolean
		 */
		public boolean isLeaf() {
			return fMap.isEmpty();
		}		
	}
	
	protected class TestVM extends AbstractVMContext {
		public TestVM(String msg) {
			super(TestNode.this);
			fMsg = msg;
		}
		public String fMsg;
		public int fDepth;
		@Override
		public String toString() {
			return "TestVM " + fMsg;
		}
		@Override
		public boolean equals(Object obj) {
			// TODO Auto-generated method stub
			if(obj instanceof TestVM) {
				TestVM test = (TestVM)obj;
				return this.fMsg.equals(test.fMsg) && this.fDepth == test.fDepth;				
			}
			return false;
		}
		@Override
		public int hashCode() {
			// TODO Auto-generated method stub
			return fMsg.hashCode() ^ fDepth;
		}
	}

	@Immutable
    protected class StackVMContext extends AbstractVMContext  implements IDMVMContext {
        private StackNodeDM fStackNode;
        
        public StackVMContext(StackNodeDM node) {
            super(TestNode.this);
            fStackNode = node;
        }
        
        //@Override
		//public IDMContext getDMContext() { return fDmc; }
        
        /**
         * The IAdaptable implementation.  If the adapter is the DM context, 
         * return the context, otherwise delegate to IDMContext.getAdapter().
         */
        @SuppressWarnings("unchecked")
		@Override
        public <T> T getAdapter(Class<T> adapter) {
            T superAdapter = super.getAdapter(adapter);
            if (superAdapter != null) {
                return superAdapter;
            } else {
                // Delegate to the Data Model to find the context.
                if (adapter.isInstance(fStackNode)) {
                    return (T)fStackNode;
                } else {
                    return fStackNode.getAdapter(adapter); //fDmc.getAdapter(adapter);
                }
            }
        }
        
        public StackNodeDM getStackNode() { return fStackNode; }
        
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof TestNode.StackVMContext)) return false;
            StackVMContext otherVmc = (StackVMContext)other;
            return TestNode.this.equals(otherVmc.getVMNode()) &&
            		fStackNode.equals(otherVmc.fStackNode);
        }
        
        @Override
        public int hashCode() {
            return TestNode.this.hashCode() + fStackNode.hashCode(); 
        }
     
        @Override
        public String toString() {
            return "StackVMContext " + fStackNode.getId(); //$NON-NLS-1$
        }

		@Override
		public IDMContext getDMContext() {
			return fStackNode.getDMContext();
		}
    }
	
	private DsfExecutor getExecutor() {
		return fSession.getExecutor();
	}
	
	private <V> V getService(Class<V> serviceClass) {
		return fServiceTracker.getService(serviceClass);
	}
	
	
	
	private DsfSession fSession;
	private AbstractDMVMProvider fProvider;
	private DsfServicesTracker fServiceTracker;
	private IElementLabelProvider fLabelProvider;

	public TestNode(AbstractDMVMProvider provider, DsfSession session)
	{
		fProvider = provider;
		fSession = session;
		fServiceTracker = new DsfServicesTracker(DsfUIPlugin.getBundleContext(), session.getId());
		fLabelProvider = createLabelProvider();
		
	}
	
	private IElementLabelProvider createLabelProvider() {
		PropertiesBasedLabelProvider provider = new PropertiesBasedLabelProvider();
		provider.setColumnInfo(
				PropertiesBasedLabelProvider.ID_COLUMN_NO_COLUMNS,
				new LabelColumnInfo(new LabelAttribute[]  {
						new LabelText (
								"???", //$NON-NLS-1$
								new String[] { })
						{
							@Override
		                    public boolean isEnabled(IStatus status, Map<String, Object> properties) {
								String funcName = (String)properties.get(TEST_ID);
								String threads = (String)properties.get(STACKNODE_THREAD_LIST);
		                        return funcName == null && threads == null;
		                    }
						},
						new LabelText (
								"{0}", //$NON-NLS-1$
								new String[] { TEST_ID })
						{
							@Override
		                    public boolean isEnabled(IStatus status, Map<String, Object> properties) {
								String funcName = (String)properties.get(TEST_ID);
								String threads = (String)properties.get(STACKNODE_THREAD_LIST);
		                        return funcName != null && threads == null;
		                    }
						},
						new LabelText (
								"{0} - Threads : {1}", //$NON-NLS-1$
								new String[] { TEST_ID, STACKNODE_THREAD_LIST })
						{
							@Override
		                    public boolean isEnabled(IStatus status, Map<String, Object> properties) {
								String funcName = (String)properties.get(TEST_ID);
								String threads = (String)properties.get(STACKNODE_THREAD_LIST);
		                        return funcName != null && threads != null;
		                    }
						},
						new LabelText (
								"??? - Thread {0}", //$NON-NLS-1$
								new String[] { STACKNODE_THREAD_LIST })
						{
							@Override
		                    public boolean isEnabled(IStatus status, Map<String, Object> properties) {
								String funcName = (String)properties.get(TEST_ID);
								String threads = (String)properties.get(STACKNODE_THREAD_LIST);
		                        return funcName == null && threads != null;
		                    }
						}
				}));
		
		return provider;
	}

	@Override
	public void update(IChildrenCountUpdate[] updates) {
		/* Behavior of ThreadVMNode :
		 * for updates :
		 * 		for update.elements :
		 * 			if element instanceof IDMVMContext 
		 * 				if context.session alive
		 * 					continue
		 * 				else
		 * 					update is invalid, cancel
		 * tldr : check if the contexts' session is alive for every update. If not, cancel update
		 */
		for(IChildrenCountUpdate update : updates) {
			if(update.getElement() instanceof TestVM) {
				int depth = ((TestVM)update.getElement()).fDepth;
				update.setChildCount(2-depth >= 0 ? 2-depth : 0);
			}
			else if(update.getElement() instanceof StackVMContext) {
				update.setChildCount(((StackVMContext)update.getElement()).getStackNode().getChildren().size());
			} else {
				update.setStatus(new Status(IStatus.ERROR, DsfUIPlugin.PLUGIN_ID, IDsfStatusConstants.NOT_SUPPORTED, "Not implemented, clients should call to update all children instead.", null)); //$NON-NLS-1$
			}
	        update.done();
		}
	}
	
	protected void fillUpdateWithVMCs(IChildrenUpdate update, StackNodeDM root) {
		StackNodeDM[] children = root.getChildren().toArray(new StackNodeDM[0]);
		int updateIdx = update.getOffset() != -1 ? update.getOffset() : 0;
		final int endIdx = updateIdx + (update.getLength() != -1 ? update.getLength() : children.length);
		while(updateIdx < endIdx && updateIdx < children.length) {
			System.out.println("Set Child " + children[updateIdx].toString() + " at " + Integer.toString(updateIdx));
			update.setChild(new StackVMContext(children[updateIdx]), updateIdx);
			updateIdx++;
		}
		
	}
	
	public void processUpdate(IChildrenUpdate update) {

		ViewerDataRequestMonitor<StackNodeDM> _rm = 
				new ViewerDataRequestMonitor<StackNodeDM>(getExecutor(), update) {
			@Override
			protected void handleCompleted() {
				if(! isSuccess()) {
					update.done();
					return;
				}
				StackNodeDM node = getData();
				if(node == null) {
					update.done();
					return;
				}
				fillUpdateWithVMCs(update, node);
				/*
				StackNodeDM[] nodes = node.getChildren().toArray(new StackNodeDM[0]);
				int updateIdx = update.getOffset() != -1 ? update.getOffset() : 0;
				int endIdx = updateIdx + (update.getLength() != -1 ? update.getLength() : nodes.length);
				while( updateIdx < endIdx && updateIdx < nodes.length) {
					System.out.println("Set child " + nodes[updateIdx].toString() + " at " + Integer.toString(updateIdx));
					update.setChild(new StackVMContext(nodes[updateIdx]), updateIdx);
					updateIdx++;
				}
				*/
				update.done();
			}
		};
		getProcesses(_rm);
	}
	
	public void processDelta(VMDelta parent, RequestMonitor rm, int flags) {
		DataRequestMonitor<StackNodeDM> _rm = 
				new DataRequestMonitor<StackNodeDM>(getExecutor(), rm) {
			@Override
			protected void handleCompleted() {
				if(! isSuccess()) {
					rm.done();
					return;
				}
				StackNodeDM node = getData();
				if(node == null) {
					rm.done();
					return;
				}
				StackNodeDM[] children = node.getChildren().toArray(new StackNodeDM[0]);
				if(children.length > 0) {
					if(fDeltaElement == null) {
						parent.addNode(children[0], flags);
					} else {
						parent.addNode(children[0], flags);
					}
					fDeltaElement = children[0];
				}
				rm.done();
			}
		};
		getProcesses(_rm);
	}
	

	@Override
	public void update(IChildrenUpdate[] updates) {
		/* Behavior of ThreadVMNode :
		 * check if the session if alive for every IDMVMContext in updates. If not, call updateElementsInSessionThread
		 * This function call getProcessesBeingDebugged
		 *		-retourne un IDMContext par thread, utilisé pour créé un VMContext par thread et l'ajouter à l'update
		 */
		
		
		for(IChildrenUpdate update : updates) {
			Object element = update.getElement();
			if(element instanceof StackVMContext) {
				fillUpdateWithVMCs(update, ((StackVMContext)element).getStackNode());
				/*
				int updateIdx = update.getOffset() != -1 ? update.getOffset() : 0;
				StackNodeDM[] nodes = ((StackVMContext)element).getStackNode().getChildren().toArray(new StackNodeDM[0]);
				for(int i = 0; i < update.getLength() && i < nodes.length; i++) {
					//update.setChild(nodes[i], updateIdx++);
					update.setChild(new StackVMContext(nodes[updateIdx]), updateIdx++);
				}
				*/
				update.done();
			} else {
				processUpdate(update);
																
			}
				
		}
		
	}

	@SuppressWarnings("restriction")
	@Override
	public void update(IHasChildrenUpdate[] updates) {
		/* Behavior of ThreadVMNode :
		 * fail update if there is a DMCMContext not alive in one of the updates' elements
		 * Called by TreeModelProvider
		 */
		for(IHasChildrenUpdate update : updates) {
			//update.setStatus(new Status(IStatus.ERROR, DsfUIPlugin.PLUGIN_ID, IDsfStatusConstants.NOT_SUPPORTED, "Not implemented, clients should call to update all children instead.", null)); //$NON-NLS-1$
			if (update.getElement() instanceof StackVMContext) {
				update.setHasChilren(!((StackVMContext)update.getElement()).getStackNode().isLeaf());
			} else if (update.getElement() instanceof TestVM) {
				int depth = ((TestVM)update.getElement()).fDepth;
				update.setHasChilren(2-depth >= 0);
			} else {
				//update.setStatus(new Status(IStatus.ERROR, DsfUIPlugin.PLUGIN_ID, IDsfStatusConstants.NOT_SUPPORTED, "Not implemented, clients should call to update all children instead.", null)); //$NON-NLS-1$
				update.setHasChilren(true);
			}
	        update.done();
		}
		
	}

	@Override
	public void update(IPropertiesUpdate[] updates) {
		/* Behavior of ThreadVMNode :
		 * For each update, find the corresponding Context.
		 * Retrieve the execution data from dsf
		 */
		 	
		for(IPropertiesUpdate update : updates) {
			if(update.getElement() instanceof TestVM) {
				String name = ((TestVM)update.getElement()).fMsg;
				update.setProperty(TEST_ID, name);
			}
			if(update.getElement() instanceof StackVMContext) {
				StackNodeDM node = ((StackVMContext)update.getElement()).getStackNode();
				update.setProperty(TEST_ID, node.getId());
				if(!node.getDMContexts().isEmpty()) {
					StringBuilder sb = new StringBuilder();
					for(IDMContext context : node.getDMContexts()) {
						if(context instanceof IMIExecutionDMContext) {
							sb.append(((IMIExecutionDMContext)context).getThreadId());
							sb.append(",");
						}
					}
					String threads = sb.toString();
					if(sb != null) {
						update.setProperty(STACKNODE_THREAD_LIST, sb.toString());
					}
				}
			}
			update.done();
		}
		
	}

	@Override
	public void update(ILabelUpdate[] updates) {
		// Call fLabelProvider's update (PropertiesBasedLabelProvider
		fLabelProvider.update(updates);
	}

	@Override
	public IVMProvider getVMProvider() {
		return fProvider;
	}

	@Override
	public int getDeltaFlags(Object event) {
		
		// Indicate if the Node needs to create a delta for the event
		if (event instanceof ISuspendedDMEvent || event instanceof IBreakpointHitDMEvent) {
			return IModelDelta.CONTENT | IModelDelta.EXPAND | IModelDelta.STATE;
		}
		return IModelDelta.NO_CHANGE;
	}
	
	public void getFrameData(IFrameDMContext[] contexts, DataRequestMonitor<String[]>rm) {
		IStack service = fServiceTracker.getService(IStack.class);
		
		CountingRequestMonitor crm = new CountingRequestMonitor(fSession.getExecutor(), rm);
		crm.setDoneCount(contexts.length);
		
		String[] array = new String[contexts.length];
		rm.setData(array);
		
		for (int i = 0; i < contexts.length; ++i) {
			int index = i;
			IFrameDMContext context = contexts[i];
			service.getFrameData(context, 
					new DataRequestMonitor<IFrameDMData>(fSession.getExecutor(), crm) {
				@Override
				protected void handleSuccess() {
					if(!isSuccess()) {
						crm.done();
						return;
					}
					array[index] =  getData().getFunction();
					crm.done();
					return;
				}
			});
		}
	}
	
	/**
	 * Retrieve the stack for each thread.
	 * @param contexts Array of IDMContext where each item represents a thread.
	 * @param rm RequestMonitor to fill with an array of StackNodeDM, each
	 * 			representing the root for a thread.
	 */
	private void getStackForThreads(IDMContext[] contexts, DataRequestMonitor<StackNodeDM[]> rm) {
		
		CountingRequestMonitor crm = new CountingRequestMonitor(fSession.getExecutor(), rm);
		crm.setDoneCount(contexts.length);
		
		/* Replace by a CountingRequestMonitor with data ? */
		StackNodeDM[] nodes = new StackNodeDM[contexts.length];
		rm.setData(nodes);
		
		for (int i = 0; i < contexts.length; ++i) {
			IDMContext context = contexts[i];
			int index = i;
			if (! (context instanceof IExecutionDMContext))
				continue;
			
			fSession.getExecutor().execute(new DsfRunnable() {
				
				@Override
				public void run() {
					IStack stackService = fServiceTracker.getService(IStack.class);
					stackService.getFrames(
							context,
							new DataRequestMonitor<IFrameDMContext []>(fSession.getExecutor(), crm) {
								@Override
								public void handleCompleted() {
									if(!isSuccess()) {
										crm.done();
										return;
									}
									DataRequestMonitor<String[]> _rm = 
											new DataRequestMonitor<String[]>(getExecutor(), crm){
										@Override
										protected void handleCompleted() {
											if(! isSuccess()) {
												crm.done();
												return;
											}
											StackNodeDM root = new StackNodeDM(null, fProvider, fSession);
											StackNodeDM child = root;
											String[] result = getData();
											String temp = "";
											for(int i = result.length - 1; i >= 0; --i) {
												temp += result[i] + ",";
												child = child.add(result[i]);
											}
											System.out.println(temp);
											child.addThread(context);
											nodes[index] = root;
											crm.done();
										}
									};
									getFrameData(getData(), _rm);
									return;
								}
							});
				}
			});
			
			
		}
	}
	
	private StackNodeDM mergeTrees(StackNodeDM first, StackNodeDM second) {
		if(first == null && second == null)
			return null;
		if(first == null)
			return second;
		if(second == null)
			return first;
		if(first.isLeaf() && second.isLeaf())
			return first.addThreads(second);
		if(first.isLeaf())
			return second.addThreads(first);
		if(second.isLeaf())
			return first.addThreads(second);
		
		StackNodeDM node = new StackNodeDM(first.getId(), first.getProvider(), first.getSession());
		node.putAll(first);
		node.putAll(second);
		node.addThreads(first).addThreads(second);
		for(Map.Entry<String, StackNodeDM> e : node.getMap().entrySet()) {
			e.setValue(mergeTrees(first.getItem(e.getKey()), second.getItem(e.getKey())));
		}
		return node;
	}
	
	private StackNodeDM mergeTreeArray(StackNodeDM[] roots) {
		StackNodeDM root = new StackNodeDM(null, fProvider, fSession);
		if( roots.length <= 0 ) 
			return root;
		
		root = roots[0];
		for(int i = 1; i < roots.length; i++) {
			root = mergeTrees(root, roots[i]);
		}
		return root;
	}
	
	/**
	 * Retrieve threads for each process
	 * @param contexts Array of IDMContext representing processes.
	 * @param rm
	 */
	private void getAllThreads(IDMContext[] contexts, DataRequestMonitor<StackNodeDM[]> rm) {
		DsfExecutor exec = getExecutor();
		CountingRequestMonitor crm = new CountingRequestMonitor(exec, rm);
		crm.setDoneCount(contexts.length);
		
		StackNodeDM[] array = new StackNodeDM[contexts.length];
		rm.setData(array);		
		
		for(int i = 0; i < contexts.length; ++i) {
			int index = i;
			IDMContext context = contexts[i];
			getExecutor().execute(new DsfRunnable() {
				@Override
				public void run() {
					IProcesses procService = getService(IProcesses.class);

					procService.getProcessesBeingDebugged(context, 
							new DataRequestMonitor<IDMContext[]>(exec, crm) {
						@Override
						public void handleCompleted() {
							if(! isSuccess()) {
								crm.done();
								return;
							}
							
							DataRequestMonitor<StackNodeDM[]> _rm =
									new DataRequestMonitor<StackNodeDM[]>(getExecutor(),
											crm) {
										@Override
										protected void handleCompleted() {
											if(! isSuccess()) {
												crm.done();
												return;
											}
											StackNodeDM root = mergeTreeArray(getData());
											array[index] = root;
											crm.done();											
										}
										
									};
							
							getStackForThreads(getData(), _rm);
							return;
						}
					});

				}
			});
		}
	}
	
	void print_debug_tree(StackNodeDM root, int level) {
		if(level == 0)
			System.out.println("-----------------------------------");
		String tab = "";
		for(int i = 0; i < level; i++) {
			tab += "\t";
		}
		String threads = "";
		for(IDMContext context : root.getDMContexts()) {
			if(context instanceof IMIExecutionDMContext) {
				threads += ((IMIExecutionDMContext)context).getThreadId() + ", ";
			}
		}
		System.out.println(tab + root.fId + ", threads : " + threads);
		for(StackNodeDM node : root.getChildren()) {
			print_debug_tree(node, level+1);
		}
	}
	
	private void getProcesses( DataRequestMonitor<StackNodeDM> rm) {
		getExecutor().execute(new DsfRunnable() {
			
			@Override
			public void run() {
				IProcesses procService = getService(IProcesses.class);
				ICommandControlService controlService = getService(ICommandControlService.class);
				
				if(controlService == null || procService == null) {
					rm.done();
					return;
				}
				
				procService.getProcessesBeingDebugged(controlService.getContext(), 
						new DataRequestMonitor<IDMContext[]>(getExecutor(), 
								rm){
							@Override
							public void handleCompleted() {
								if(! isSuccess()) {
									rm.done();
									return;
								}
								DataRequestMonitor<StackNodeDM[]> _rm = 
										new DataRequestMonitor<StackNodeDM[]>(getExecutor(),
												rm) {
									@Override
									protected void handleCompleted() {
										if(! isSuccess()) {
											rm.done();
											return;
										}
										StackNodeDM root = mergeTreeArray(getData());
										rm.setData(root);
										print_debug_tree(root, 0);
										rm.done();
									}
								};
								getAllThreads(getData(), _rm);
								return;
							}
					
				});
			}
		});
	}
	
	

	@Override
	public void buildDelta(Object event, VMDelta parent, int nodeOffset, RequestMonitor requestMonitor) {

		
		if( event instanceof IBreakpointHitDMEvent || event instanceof ISuspendedDMEvent) {
			IContainerSuspendedDMEvent csEvent = (IContainerSuspendedDMEvent)event;
			IExecutionDMContext triggeringCtx = csEvent.getTriggeringContexts().length != 0 
					? csEvent.getTriggeringContexts()[0] : null;
					
			parent.setFlags(parent.getFlags() | IModelDelta.CONTENT | IModelDelta.EXPAND);
			String dummy = "dummy";
			StackNodeDM node = new StackNodeDM(null, fProvider, fSession);

			if(fDeltaElement == null) {
				processDelta(parent, requestMonitor, IModelDelta.CONTENT | IModelDelta.EXPAND);
				//parent.addNode(node, IModelDelta.CONTENT | IModelDelta.EXPAND);
				//requestMonitor.done();
			} else {
				//parent.addNode(fDeltaElement, IModelDelta.REMOVED);
				processDelta(parent, requestMonitor, IModelDelta.CONTENT  | IModelDelta.EXPAND | IModelDelta.REPLACED);
				//parent.addNode("test" + Long.toString(fInitialized), triggeringCtx, IModelDelta.CONTENT | IModelDelta.EXPAND | IModelDelta.REPLACED);
			}
		} else if (event instanceof ICommandControlShutdownDMEvent) {
			parent.setFlags(parent.getFlags() | IModelDelta.CONTENT);
		} else if (event instanceof IThreadRemovedDMEvent) {
			parent.setFlags(parent.getFlags() | IModelDelta.CONTENT);
		}
	}

	@SuppressWarnings("restriction")
	@Override
	public void getContextsForEvent(VMDelta parentDelta, Object event, DataRequestMonitor<IVMContext[]> rm) {
        rm.setStatus(new Status(IStatus.ERROR, DsfUIPlugin.PLUGIN_ID, IDsfStatusConstants.NOT_SUPPORTED, "", null)); //$NON-NLS-1$
        rm.done();
	}

	@Override
	public void dispose() {
		fServiceTracker.dispose();
	}

}
