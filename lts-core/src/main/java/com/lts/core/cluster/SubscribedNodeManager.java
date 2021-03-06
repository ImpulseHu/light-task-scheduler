package com.lts.core.cluster;


import com.lts.core.Application;
import com.lts.core.commons.collect.ConcurrentHashSet;
import com.lts.core.commons.utils.CollectionUtils;
import com.lts.core.commons.utils.ListUtils;
import com.lts.core.constant.EcTopic;
import com.lts.core.listener.NodeChangeListener;
import com.lts.core.logger.Logger;
import com.lts.core.logger.LoggerFactory;
import com.lts.ec.EventInfo;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Robert HG (254963746@qq.com) on 6/22/14.
 *         节点管理 (主要用于管理自己关注的节点)
 */
public class SubscribedNodeManager implements NodeChangeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscribedNodeManager.class);
    private final ConcurrentHashMap<NodeType, Set<Node>> NODES = new ConcurrentHashMap<NodeType, Set<Node>>();

    private Application application;

    public SubscribedNodeManager(Application application) {
        this.application = application;
    }

    /**
     * 添加监听的节点
     */
    private void addNode(Node node) {
        if ((NodeType.JOB_TRACKER.equals(node.getNodeType()))) {
            // 如果增加的JobTracker节点，那么直接添加，因为三种节点都需要监听
            _addNode(node);
        } else if (NodeType.JOB_TRACKER.equals(application.getConfig().getNodeType())) {
            // 如果当天节点是JobTracker节点，那么直接添加，因为JobTracker节点要监听三种节点
            _addNode(node);
        } else if (application.getConfig().getNodeType().equals(node.getNodeType())
                && application.getConfig().getNodeGroup().equals(node.getGroup())) {
            // 剩下这种情况是JobClient和TaskTracker都只监听和自己同一个group的节点
            _addNode(node);
        }
    }

    private void _addNode(Node node) {
        Set<Node> nodeSet = NODES.get(node.getNodeType());
        if (CollectionUtils.isEmpty(nodeSet)) {
            nodeSet = new ConcurrentHashSet<Node>();
            Set<Node> oldNodeList = NODES.putIfAbsent(node.getNodeType(), nodeSet);
            if (oldNodeList != null) {
                nodeSet = oldNodeList;
            }
        }
        nodeSet.add(node);
        EventInfo eventInfo = new EventInfo(EcTopic.NODE_ADD);
        eventInfo.setParam("node", node);
        application.getEventCenter().publishSync(eventInfo);
        LOGGER.info("Add {}", node);
    }

    public List<Node> getNodeList(final NodeType nodeType, final String nodeGroup) {

        Set<Node> nodes = NODES.get(nodeType);

        return ListUtils.filter(CollectionUtils.setToList(nodes), new ListUtils.Filter<Node>() {
            @Override
            public boolean filter(Node node) {
                return node.getGroup().equals(nodeGroup);
            }
        });
    }

    public List<Node> getNodeList(NodeType nodeType) {
        return CollectionUtils.setToList(NODES.get(nodeType));
    }

    private void removeNode(Node delNode) {
        Set<Node> nodeSet = NODES.get(delNode.getNodeType());

        if (CollectionUtils.isNotEmpty(nodeSet)) {
            for (Node node : nodeSet) {
                if (node.getIdentity().equals(delNode.getIdentity())) {
                    nodeSet.remove(node);
                    EventInfo eventInfo = new EventInfo(EcTopic.NODE_REMOVE);
                    eventInfo.setParam("node", node);
                    application.getEventCenter().publishSync(eventInfo);
                    LOGGER.info("Remove {}", node);
                }
            }
        }
    }

    @Override
    public void addNodes(List<Node> nodes) {
        if (CollectionUtils.isEmpty(nodes)) {
            return;
        }
        for (Node node : nodes) {
            addNode(node);
        }
    }

    @Override
    public void removeNodes(List<Node> nodes) {
        if (CollectionUtils.isEmpty(nodes)) {
            return;
        }
        for (Node node : nodes) {
            removeNode(node);
        }
    }
}
